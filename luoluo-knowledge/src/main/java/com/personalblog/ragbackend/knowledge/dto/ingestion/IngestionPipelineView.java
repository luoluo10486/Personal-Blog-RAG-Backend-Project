package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.List;

public record IngestionPipelineView(
        String id,
        String name,
        String description,
        String createdBy,
        List<IngestionPipelineNodeView> nodes,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
