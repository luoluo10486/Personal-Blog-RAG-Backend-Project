package com.personalblog.ragbackend.knowledge.service.ingest.pipeline;

import java.util.Map;

public record IngestionPipelineNodeConfig(
        String nodeId,
        String nodeType,
        Map<String, Object> settings,
        Map<String, Object> condition,
        String nextNodeId
) {
}
