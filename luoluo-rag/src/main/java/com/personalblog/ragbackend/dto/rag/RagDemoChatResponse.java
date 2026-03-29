package com.personalblog.ragbackend.dto.rag;

/**
 * RAG 演示问答响应，返回答案正文和 token 消耗统计。
 */
public record RagDemoChatResponse(
        String requestId,
        String model,
        String answer,
        String finishReason,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
