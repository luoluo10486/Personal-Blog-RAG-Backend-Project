package com.personalblog.ragbackend.knowledge.dto.ingestion;

public record IngestionTaskCreateRequest(
        String baseCode,
        Long pipelineId,
        String sourceType,
        String sourceLocation,
        String sourceFileName
) {
}
