package com.personalblog.ragbackend.knowledge.dto.ingestion;

import com.personalblog.ragbackend.rag.core.vector.VectorSpaceId;

import java.util.Map;

public record IngestionTaskCreateRequest(
        String pipelineId,
        DocumentSourceRequest source,
        Map<String, Object> metadata,
        VectorSpaceId vectorSpaceId
) {
}
