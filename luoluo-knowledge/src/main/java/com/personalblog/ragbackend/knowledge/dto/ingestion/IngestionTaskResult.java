package com.personalblog.ragbackend.knowledge.dto.ingestion;

public record IngestionTaskResult(
        String taskId,
        String pipelineId,
        String status,
        Integer chunkCount,
        String message
) {
}
