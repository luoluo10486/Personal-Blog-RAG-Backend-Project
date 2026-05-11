package com.personalblog.ragbackend.rag.core.memory;

import com.personalblog.ragbackend.infra.convention.ChatMessage;

import java.util.List;

public interface ConversationMemoryStore {
    List<ChatMessage> loadHistory(String conversationId, Long userId);

    String append(String conversationId, Long userId, ChatMessage message);
}
