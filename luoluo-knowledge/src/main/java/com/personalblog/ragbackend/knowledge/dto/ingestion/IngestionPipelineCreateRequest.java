package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.List;

public record IngestionPipelineCreateRequest(
        String name,
        String description,
        List<IngestionPipelineNodeRequest> nodes
) {
}
