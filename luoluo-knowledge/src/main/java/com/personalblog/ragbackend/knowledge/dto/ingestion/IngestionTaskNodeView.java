package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.Map;

public record IngestionTaskNodeView(
        Long id,
        Long taskId,
        Long pipelineId,
        String nodeId,
        String nodeType,
        Integer nodeOrder,
        String status,
        Long durationMs,
        String message,
        String errorMessage,
        Map<String, Object> output,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
