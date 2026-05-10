package com.personalblog.ragbackend.knowledge.service.rag.memory;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;

public interface ConversationMemorySummaryService {
    void compressIfNeeded(String conversationId, Long userId, ChatMessage message);

    ChatMessage loadLatestSummary(String conversationId, Long userId);

    ChatMessage decorateIfNeeded(ChatMessage summary);
}
