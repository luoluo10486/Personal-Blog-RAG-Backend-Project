package com.personalblog.ragbackend.dto.rag;

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
