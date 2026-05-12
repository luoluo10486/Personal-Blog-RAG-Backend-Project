package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.Map;

public record IngestionTaskLogView(
        String nodeId,
        String nodeType,
        String message,
        long durationMs,
        boolean success,
        String error,
        Map<String, Object> output
) {
}
