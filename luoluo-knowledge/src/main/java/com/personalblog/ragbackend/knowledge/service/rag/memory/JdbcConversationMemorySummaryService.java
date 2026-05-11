package com.personalblog.ragbackend.knowledge.service.rag.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.config.RagMemoryProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import jakarta.annotation.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class JdbcConversationMemorySummaryService implements ConversationMemorySummaryService {
    private static final String SUMMARY_PREFIX = "对话摘要：";
    private static final String SUMMARY_LOCK_PREFIX = "ragent:memory:summary:lock:";
    private static final String SUMMARY_PROMPT_PATH = "prompt/conversation-summary.st";
    private final ConcurrentMap<String, RLock> locks = new ConcurrentHashMap<>();

    private final RagConversationMessageMapper messageMapper;
    private final RagConversationSummaryMapper summaryMapper;
    private final RagMemoryProperties memoryProperties;
    private final PromptTemplateLoader promptTemplateLoader;
    private final RedissonClient redissonClient;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final Executor memorySummaryExecutor;

    public JdbcConversationMemorySummaryService(RagConversationMessageMapper messageMapper,
                                                RagConversationSummaryMapper summaryMapper,
                                                RagMemoryProperties memoryProperties,
                                                PromptTemplateLoader promptTemplateLoader,
                                                RedissonClient redissonClient,
                                                ObjectProvider<LLMService> llmServiceProvider,
                                                @Qualifier("memorySummaryThreadPoolExecutor") Executor memorySummaryExecutor) {
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
        this.memoryProperties = memoryProperties;
        this.promptTemplateLoader = promptTemplateLoader;
        this.redissonClient = redissonClient;
        this.llmServiceProvider = llmServiceProvider;
        this.memorySummaryExecutor = memorySummaryExecutor;
    }

    @Override
    public void compressIfNeeded(String conversationId, Long userId, ChatMessage message) {
        if (!memoryProperties.isSummaryEnabled()) {
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
        RagConversationSummaryEntity summary = summaryMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationSummaryEntity.class)
                        .eq(RagConversationSummaryEntity::getConversationId, conversationId)
                        .eq(RagConversationSummaryEntity::getUserId, userId)
                        .eq(RagConversationSummaryEntity::getDeleted, 0)
                        .orderByDesc(RagConversationSummaryEntity::getId)
                        .last("limit 1")
        );
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
            long total = messageMapper.selectCount(
                    Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                            .eq(RagConversationMessageEntity::getConversationId, conversationId)
                            .eq(RagConversationMessageEntity::getUserId, userId)
                            .eq(RagConversationMessageEntity::getRole, "user")
                            .eq(RagConversationMessageEntity::getDeleted, 0)
            );
            if (total < memoryProperties.getSummaryStartTurns()) {
                return;
            }

            RagConversationSummaryEntity latestSummary = summaryMapper.selectOne(
                    Wrappers.lambdaQuery(RagConversationSummaryEntity.class)
                            .eq(RagConversationSummaryEntity::getConversationId, conversationId)
                            .eq(RagConversationSummaryEntity::getUserId, userId)
                            .eq(RagConversationSummaryEntity::getDeleted, 0)
                            .orderByDesc(RagConversationSummaryEntity::getId)
                            .last("limit 1")
            );
            List<RagConversationMessageEntity> latestUserTurns = messageMapper.selectList(
                    Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                            .eq(RagConversationMessageEntity::getConversationId, conversationId)
                            .eq(RagConversationMessageEntity::getUserId, userId)
                            .eq(RagConversationMessageEntity::getRole, "user")
                            .eq(RagConversationMessageEntity::getDeleted, 0)
                            .orderByDesc(RagConversationMessageEntity::getCreatedAt)
                            .last("limit " + memoryProperties.getHistoryKeepTurns())
            );
            if (CollUtil.isEmpty(latestUserTurns)) {
                return;
            }

            Long cutoffId = latestUserTurns.get(latestUserTurns.size() - 1).getId();
            if (cutoffId == null) {
                return;
            }

            Long afterId = resolveSummaryStartId(conversationId, userId, latestSummary);
            if (afterId != null && afterId >= cutoffId) {
                return;
            }

            List<RagConversationMessageEntity> toSummarize = messageMapper.selectList(
                    Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                            .eq(RagConversationMessageEntity::getConversationId, conversationId)
                            .eq(RagConversationMessageEntity::getUserId, userId)
                            .in(RagConversationMessageEntity::getRole, "user", "assistant")
                            .eq(RagConversationMessageEntity::getDeleted, 0)
                            .gt(afterId != null, RagConversationMessageEntity::getId, afterId)
                            .lt(RagConversationMessageEntity::getId, cutoffId)
                            .orderByAsc(RagConversationMessageEntity::getId)
            );
            if (CollUtil.isEmpty(toSummarize)) {
                return;
            }

            Long lastMessageId = toSummarize.stream()
                    .map(RagConversationMessageEntity::getId)
                    .filter(Objects::nonNull)
                    .reduce((left, right) -> right)
                    .orElse(null);
            if (lastMessageId == null) {
                return;
            }

            String existingSummary = latestSummary == null ? "" : latestSummary.getContent();
            String summary = summarizeMessages(toSummarize, existingSummary);
            if (StrUtil.isBlank(summary)) {
                return;
            }

            RagConversationSummaryEntity record = new RagConversationSummaryEntity();
            record.setConversationId(conversationId);
            record.setUserId(userId);
            record.setLastMessageId(lastMessageId);
            record.setContent(summary);
            record.setDeleted(0);
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            summaryMapper.insert(record);
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

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
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
            summaryMessages.add(ChatMessage.assistant("Existing summary for merge only:\n" + existingSummary.trim()));
        }
        summaryMessages.addAll(histories);
        summaryMessages.add(ChatMessage.user("Merge the content above and output a new concise single-paragraph summary."));

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

    private ChatMessage toChatMessage(RagConversationSummaryEntity record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        return ChatMessage.system(record.getContent());
    }

    private Long resolveSummaryStartId(String conversationId, Long userId, RagConversationSummaryEntity summary) {
        if (summary == null) {
            return null;
        }
        if (summary.getLastMessageId() != null) {
            return summary.getLastMessageId();
        }

        LocalDateTime after = summary.getUpdatedAt();
        if (after == null) {
            after = summary.getCreatedAt();
        }
        if (after == null) {
            return null;
        }
        RagConversationMessageEntity record = messageMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, userId)
                        .eq(RagConversationMessageEntity::getDeleted, 0)
                        .le(RagConversationMessageEntity::getCreatedAt, after)
                        .orderByDesc(RagConversationMessageEntity::getId)
                        .last("limit 1")
        );
        return record == null ? null : record.getId();
    }
}
