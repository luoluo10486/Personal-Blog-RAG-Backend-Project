package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.Map;

public record IngestionTaskCreateRequest(
        String pipelineId,
        DocumentSourceRequest source,
        Map<String, Object> metadata,
        Map<String, Object> vectorSpaceId
) {
}
