package com.personalblog.ragbackend.dto.rag;

/**
 * 多轮对话中的单条历史消息。
 */
public record RagConversationMessage(
        String role,
        String content
) {
}
