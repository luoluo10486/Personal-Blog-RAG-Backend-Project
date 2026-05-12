package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.Map;

public record IngestionTaskNodeView(
        String id,
        String taskId,
        String pipelineId,
        String nodeId,
        String nodeType,
        Integer nodeOrder,
        String status,
        Long durationMs,
        String message,
        String errorMessage,
        Map<String, Object> output,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
