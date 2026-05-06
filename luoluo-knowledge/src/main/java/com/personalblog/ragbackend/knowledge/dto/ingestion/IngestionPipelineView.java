package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.List;

public record IngestionPipelineView(
        Long id,
        String name,
        String description,
        Long createdBy,
        Long updatedBy,
        List<IngestionPipelineNodeView> nodes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
