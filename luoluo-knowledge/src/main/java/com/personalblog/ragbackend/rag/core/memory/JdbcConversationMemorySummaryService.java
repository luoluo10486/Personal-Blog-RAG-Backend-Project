package com.personalblog.ragbackend.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.service.ConversationGroupService;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import com.personalblog.ragbackend.rag.service.bo.ConversationSummaryBO;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JdbcConversationMemorySummaryService implements ConversationMemorySummaryService {
    private static final String SUMMARY_LOCK_PREFIX = "ragent:memory:summary:lock:";
    private static final String SUMMARY_PROMPT_PATH = "prompt/conversation-summary.st";
    private final ConcurrentMap<String, RLock> locks = new ConcurrentHashMap<>();

    private final ConversationGroupService conversationGroupService;
    private final ConversationMessageService conversationMessageService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;
    private final Executor memorySummaryExecutor;

    public JdbcConversationMemorySummaryService(ConversationGroupService conversationGroupService,
                                                ConversationMessageService conversationMessageService,
                                                MemoryProperties memoryProperties,
                                                LLMService llmService,
                                                PromptTemplateLoader promptTemplateLoader,
                                                RedissonClient redissonClient,
                                                @Qualifier("memorySummaryExecutor") Executor memorySummaryExecutor) {
        this.conversationGroupService = conversationGroupService;
        this.conversationMessageService = conversationMessageService;
        this.memoryProperties = memoryProperties;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.redissonClient = redissonClient;
        this.memorySummaryExecutor = memorySummaryExecutor;
    }

    @Override
    public void compressIfNeeded(String conversationId, Long userId, ChatMessage message) {
        if (!Boolean.TRUE.equals(memoryProperties.getSummaryEnabled())) {
            return;
        }
        if (message == null || message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId, userId), memorySummaryExecutor)
                .exceptionally(exception -> {
                    log.error("conversation memory summary async task failed, conversationId={}, userId={}",
                            conversationId, userId, exception);
                    return null;
                });
    }

    @Override
    public ChatMessage loadLatestSummary(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return null;
        }
        RagConversationSummaryEntity summary = conversationGroupService.findLatestSummary(conversationId, String.valueOf(userId));
        return toChatMessage(summary);
    }

    @Override
    public ChatMessage decorateIfNeeded(ChatMessage summary) {
        if (summary == null || StrUtil.isBlank(summary.getContent())) {
            return summary;
        }
        String wrapped = promptTemplateLoader.renderSection(
                RAGConstant.CONTEXT_FORMAT_PATH,
                "summary-wrapper",
                Map.of("content", summary.getContent().trim())
        );
        return ChatMessage.system(wrapped);
    }

    private void doCompressIfNeeded(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return;
        }

        String lockKey = SUMMARY_LOCK_PREFIX + userId + ":" + conversationId.trim();
        RLock lock = locks.computeIfAbsent(lockKey, ignored -> redissonClient.getLock(lockKey));
        if (!lock.tryLock()) {
            return;
        }
        try {
            long total = conversationGroupService.countUserMessages(conversationId, String.valueOf(userId));
            if (total < memoryProperties.getSummaryStartTurns()) {
                return;
            }

            RagConversationSummaryEntity latestSummary = conversationGroupService.findLatestSummary(conversationId, String.valueOf(userId));
            List<RagConversationMessageEntity> latestUserTurns = conversationGroupService.listLatestUserOnlyMessages(
                    conversationId,
                    String.valueOf(userId),
                    memoryProperties.getHistoryKeepTurns()
            );
            if (CollUtil.isEmpty(latestUserTurns)) {
                return;
            }

            String cutoffId = resolveCutoffId(latestUserTurns);
            if (StrUtil.isBlank(cutoffId)) {
                return;
            }

            String afterId = resolveSummaryStartId(conversationId, userId, latestSummary);
            if (afterId != null && Long.parseLong(afterId) >= Long.parseLong(cutoffId)) {
                return;
            }

            List<RagConversationMessageEntity> toSummarize = conversationGroupService.listMessagesBetweenIds(
                    conversationId,
                    String.valueOf(userId),
                    afterId,
                    cutoffId
            );
            if (CollUtil.isEmpty(toSummarize)) {
                return;
            }

            String lastMessageId = resolveLastMessageId(toSummarize);
            if (StrUtil.isBlank(lastMessageId)) {
                return;
            }

            String existingSummary = latestSummary == null ? "" : latestSummary.getContent();
            String summary = summarizeMessages(toSummarize, existingSummary);
            if (StrUtil.isBlank(summary)) {
                return;
            }

            ConversationSummaryBO summaryRecord = ConversationSummaryBO.builder()
                    .conversationId(conversationId)
                    .userId(String.valueOf(userId))
                    .content(summary)
                    .lastMessageId(lastMessageId)
                    .build();
            conversationMessageService.addMessageSummary(summaryRecord);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String summarizeMessages(List<RagConversationMessageEntity> messages, String existingSummary) {
        List<ChatMessage> histories = toHistoryMessages(messages);
        if (CollUtil.isEmpty(histories)) {
            return existingSummary;
        }

        int summaryMaxChars = memoryProperties.getSummaryMaxChars();
        List<ChatMessage> summaryMessages = new ArrayList<>();
        String summaryPrompt = promptTemplateLoader.render(
                SUMMARY_PROMPT_PATH,
                Map.of("summary_max_chars", String.valueOf(summaryMaxChars))
        );
        summaryMessages.add(ChatMessage.system(summaryPrompt));

        if (StrUtil.isNotBlank(existingSummary)) {
            summaryMessages.add(ChatMessage.assistant(
                    "历史摘要（仅用于合并去重，不得作为新事实来源；若与本轮对话冲突，以本轮对话为准）：\n"
                            + existingSummary.trim()
            ));
        }
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user("合并以上对话与历史摘要，去重后输出更新摘要。要求：严格不超过 "
                + summaryMaxChars + " 个字符；仅一行。"));

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(summaryMessages)
                    .temperature(0.3D)
                    .topP(0.9D)
                    .thinking(false)
                    .build();
            return llmService.chat(request);
        } catch (Exception exception) {
            log.error("conversation summary generation failed, messages={}", messages.size(), exception);
            return existingSummary;
        }
    }

    private List<ChatMessage> toHistoryMessages(List<RagConversationMessageEntity> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null
                        && StrUtil.isNotBlank(item.getContent())
                        && StrUtil.isNotBlank(item.getRole()))
                .map(item -> {
                    String role = item.getRole().toLowerCase();
                    if ("user".equals(role)) {
                        return ChatMessage.user(item.getContent());
                    }
                    if ("assistant".equals(role)) {
                        return ChatMessage.assistant(item.getContent(), item.getThinkingContent(), item.getThinkingDuration());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChatMessage toChatMessage(@Nullable RagConversationSummaryEntity record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        return ChatMessage.system(record.getContent());
    }

    private String resolveSummaryStartId(String conversationId, Long userId, RagConversationSummaryEntity summary) {
        if (summary == null) {
            return null;
        }
        if (summary.getLastMessageId() != null) {
            return String.valueOf(summary.getLastMessageId());
        }

        Date after = summary.getUpdatedAt() == null ? null : Date.from(summary.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        if (after == null && summary.getCreatedAt() != null) {
            after = Date.from(summary.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (after == null) {
            return null;
        }
        return conversationGroupService.findMaxMessageIdAtOrBefore(conversationId, String.valueOf(userId), after);
    }

    private String resolveCutoffId(List<RagConversationMessageEntity> latestUserTurns) {
        if (CollUtil.isEmpty(latestUserTurns)) {
            return null;
        }
        RagConversationMessageEntity oldest = latestUserTurns.get(latestUserTurns.size() - 1);
        return oldest == null || oldest.getId() == null ? null : String.valueOf(oldest.getId());
    }

    private String resolveLastMessageId(List<RagConversationMessageEntity> toSummarize) {
        for (int i = toSummarize.size() - 1; i >= 0; i--) {
            RagConversationMessageEntity item = toSummarize.get(i);
            if (item != null && item.getId() != null) {
                return String.valueOf(item.getId());
            }
        }
        return null;
    }
}
