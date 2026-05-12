package com.personalblog.ragbackend.knowledge.service.ingest;

public record KnowledgeIngestionNodeLog(
        String nodeId,
        String nodeType,
        int nodeOrder,
        String status,
        long durationMs,
        String message,
        String errorMessage,
        String outputJson
) {
    public KnowledgeIngestionNodeLog(String nodeType,
                                     int nodeOrder,
                                     String status,
                                     long durationMs,
                                     String message,
                                     String errorMessage,
                                     String outputJson) {
        this(nodeType, nodeType, nodeOrder, status, durationMs, message, errorMessage, outputJson);
    }
}
