package com.personalblog.ragbackend.knowledge.service.rag.memory;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;

import java.util.List;

public interface ConversationMemoryStore {
    List<ChatMessage> loadHistory(String conversationId, Long userId);

    String append(String conversationId, Long userId, ChatMessage message);
}
