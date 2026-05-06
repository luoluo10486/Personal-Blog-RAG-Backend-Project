package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.Map;

public record IngestionPipelineNodeRequest(
        String nodeId,
        String nodeType,
        String nextNodeId,
        Map<String, Object> settings,
        Map<String, Object> condition
) {
}
