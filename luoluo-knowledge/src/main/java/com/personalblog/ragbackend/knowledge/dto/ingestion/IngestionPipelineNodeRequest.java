package com.personalblog.ragbackend.knowledge.dto.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

public record IngestionPipelineNodeRequest(
        String nodeId,
        String nodeType,
        JsonNode settings,
        JsonNode condition,
        String nextNodeId
) {
}
