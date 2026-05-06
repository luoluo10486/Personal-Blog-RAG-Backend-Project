package com.personalblog.ragbackend.knowledge.dto.ingestion;

public record IngestionTaskResult(
        Long taskId,
        Long pipelineId,
        String status,
        Integer chunkCount,
        String message
) {
}
