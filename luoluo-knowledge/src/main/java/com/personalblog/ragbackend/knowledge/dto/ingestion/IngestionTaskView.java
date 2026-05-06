package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IngestionTaskView(
        Long id,
        Long pipelineId,
        Long kbId,
        Long docId,
        String sourceType,
        String sourceLocation,
        String sourceFileName,
        String status,
        Integer chunkCount,
        String errorMessage,
        List<IngestionTaskNodeView> logs,
        Map<String, Object> metadata,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
