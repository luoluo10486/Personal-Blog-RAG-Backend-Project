package com.personalblog.ragbackend.knowledge.service.ingest.pipeline;

import java.util.List;

public record IngestionPipelineDefinition(
        String id,
        String name,
        String description,
        List<IngestionPipelineNodeConfig> nodes
) {
}
