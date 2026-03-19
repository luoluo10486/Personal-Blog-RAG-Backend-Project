package com.personalblog.ragbackend.model;

public record RetrievedChunk(
        String id,
        String title,
        String content,
        double score
) {
}
