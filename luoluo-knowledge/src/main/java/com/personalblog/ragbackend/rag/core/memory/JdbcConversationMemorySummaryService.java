package com.personalblog.ragbackend.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.service.ConversationGroupService;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import com.personalblog.ragbackend.rag.service.bo.ConversationSummaryBO;
import jakarta.annotation.Nullable;
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

@Service
public class JdbcConversationMemorySummaryService implements ConversationMemorySummaryService {
    private static final String SUMMARY_PREFIX = "对话摘要：";
    private static final String SUMMARY_LOCK_PREFIX = "ragent:memory:summary:lock:";
    private static final String SUMMARY_PROMPT_PATH = "prompt/conversation-summary.st";
    private final ConcurrentMap<String, RLock> locks = new ConcurrentHashMap<>();

    private final ConversationGroupService conversationGroupService;
    private final ConversationMessageService conversationMessageService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;

    @Qualifier("memorySummaryThreadPoolExecutor")
    private final Executor memorySummaryExecutor;

    public JdbcConversationMemorySummaryService(ConversationGroupService conversationGroupService,
                                                ConversationMessageService conversationMessageService,
                                                MemoryProperties memoryProperties,
                                                LLMService llmService,
                                                PromptTemplateLoader promptTemplateLoader,
                                                RedissonClient redissonClient,
                                                @Qualifier("memorySummaryThreadPoolExecutor") Executor memorySummaryExecutor) {
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
                .exceptionally(ex -> null);
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
        String content = summary.getContent().trim();
        if (content.startsWith(SUMMARY_PREFIX) || content.startsWith("摘要：")) {
            return summary;
        }
        return ChatMessage.system(SUMMARY_PREFIX + content);
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
            List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> latestUserTurns =
                    conversationGroupService.listLatestUserOnlyMessages(
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

            List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> toSummarize =
                    conversationGroupService.listMessagesBetweenIds(
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

    private String summarizeMessages(List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> messages,
                                     String existingSummary) {
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
                    "鍘嗗彶鎽樿锛堜粎鐢ㄤ簬鍚堝苟鍘婚噸锛屼笉寰椾綔涓轰簨瀹炴潵婧愶紱濡備笌鏈疆瀵硅瘽鍐茬獊锛屼互鏈疆瀵硅瘽涓哄噯锛夛細\n"
                            + existingSummary.trim()
            ));
        }
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user("合并以上对话与历史摘要，去重后输出更新摘要。要求：严格不超过 " + summaryMaxChars + " 字符；仅一行。"));

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(summaryMessages)
                    .temperature(0.3D)
                    .topP(0.9D)
                    .thinking(false)
                    .build();
            return llmService.chat(request);
        } catch (Exception ignored) {
            return existingSummary;
        }
    }

    private List<ChatMessage> toHistoryMessages(List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> messages) {
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
        String afterId = conversationGroupService.findMaxMessageIdAtOrBefore(conversationId, String.valueOf(userId), after);
        return afterId;
    }

    private String resolveCutoffId(List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> latestUserTurns) {
        if (CollUtil.isEmpty(latestUserTurns)) {
            return null;
        }
        com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity oldest = latestUserTurns.get(latestUserTurns.size() - 1);
        return oldest == null || oldest.getId() == null ? null : String.valueOf(oldest.getId());
    }

    private String resolveLastMessageId(List<com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity> toSummarize) {
        for (int i = toSummarize.size() - 1; i >= 0; i--) {
            com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity item = toSummarize.get(i);
            if (item != null && item.getId() != null) {
                return String.valueOf(item.getId());
            }
        }
        return null;
    }
}


