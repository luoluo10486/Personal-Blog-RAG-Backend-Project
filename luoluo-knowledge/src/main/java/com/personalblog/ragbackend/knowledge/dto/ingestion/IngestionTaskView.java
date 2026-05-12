package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IngestionTaskView(
        String id,
        String pipelineId,
        String sourceType,
        String sourceLocation,
        String sourceFileName,
        String status,
        Integer chunkCount,
        String errorMessage,
        List<IngestionTaskLogView> logs,
        Map<String, Object> metadata,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String createdBy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
