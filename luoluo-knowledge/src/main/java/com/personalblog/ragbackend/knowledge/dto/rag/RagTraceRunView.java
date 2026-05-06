package com.personalblog.ragbackend.knowledge.dto.rag;

import java.time.LocalDateTime;

public record RagTraceRunView(
        Long id,
        String traceId,
        String traceName,
        String entryMethod,
        String conversationId,
        String taskId,
        Long userId,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String extraData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
