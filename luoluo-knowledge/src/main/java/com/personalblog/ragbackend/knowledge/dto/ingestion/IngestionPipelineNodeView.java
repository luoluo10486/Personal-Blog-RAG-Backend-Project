package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.Map;

public record IngestionPipelineNodeView(
        Long id,
        Long pipelineId,
        String nodeId,
        String nodeType,
        String nextNodeId,
        Map<String, Object> settings,
        Map<String, Object> condition,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
