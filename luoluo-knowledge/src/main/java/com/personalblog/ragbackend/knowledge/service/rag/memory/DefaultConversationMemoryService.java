package com.personalblog.ragbackend.knowledge.service.rag.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class DefaultConversationMemoryService implements ConversationMemoryService {
    private final ConversationMemoryStore memoryStore;
    private final ConversationMemorySummaryService summaryService;
    private final Executor memoryLoadExecutor;

    public DefaultConversationMemoryService(ConversationMemoryStore memoryStore,
                                            ConversationMemorySummaryService summaryService,
                                            @Qualifier("memoryLoadThreadPoolExecutor") Executor memoryLoadExecutor) {
        this.memoryStore = memoryStore;
        this.summaryService = summaryService;
        this.memoryLoadExecutor = memoryLoadExecutor;
    }

    @Override
    public List<ChatMessage> load(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return List.of();
        }
        try {
            CompletableFuture<ChatMessage> summaryFuture = CompletableFuture.supplyAsync(
                    () -> loadSummaryWithFallback(conversationId, userId),
                    memoryLoadExecutor
            );
            CompletableFuture<List<ChatMessage>> historyFuture = CompletableFuture.supplyAsync(
                    () -> loadHistoryWithFallback(conversationId, userId),
                    memoryLoadExecutor
            );
            return CompletableFuture.allOf(summaryFuture, historyFuture)
                    .thenApply(ignored -> attachSummary(summaryFuture.join(), historyFuture.join()))
                    .join();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public String append(String conversationId, Long userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || userId == null || message == null) {
            return null;
        }
        String messageId = memoryStore.append(conversationId, userId, message);
        if (messageId != null) {
            summaryService.compressIfNeeded(conversationId, userId, message);
        }
        return messageId;
    }

    private ChatMessage loadSummaryWithFallback(String conversationId, Long userId) {
        try {
            return summaryService.loadLatestSummary(conversationId, userId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<ChatMessage> loadHistoryWithFallback(String conversationId, Long userId) {
        try {
            List<ChatMessage> history = memoryStore.loadHistory(conversationId, userId);
            return history == null ? List.of() : history;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ChatMessage> attachSummary(ChatMessage summary, List<ChatMessage> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        if (summary == null) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<>();
        result.add(summaryService.decorateIfNeeded(summary));
        result.addAll(messages);
        return result;
    }
}
