package com.personalblog.ragbackend.knowledge.service.ingest;

public record KnowledgeIngestionNodeLog(
        String nodeType,
        int nodeOrder,
        String status,
        long durationMs,
        String message,
        String errorMessage,
        String outputJson
) {
}
