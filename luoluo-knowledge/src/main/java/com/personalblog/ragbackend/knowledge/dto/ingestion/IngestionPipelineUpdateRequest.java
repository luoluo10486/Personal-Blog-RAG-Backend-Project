package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.List;

public record IngestionPipelineUpdateRequest(
        String name,
        String description,
        List<IngestionPipelineNodeRequest> nodes
) {
}
