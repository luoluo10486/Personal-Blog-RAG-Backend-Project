package com.personalblog.ragbackend.knowledge.dto.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

public record IngestionPipelineNodeView(
        String id,
        String nodeId,
        String nodeType,
        JsonNode settings,
        JsonNode condition,
        String nextNodeId
) {
}
