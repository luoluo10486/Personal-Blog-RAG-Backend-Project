package com.personalblog.ragbackend.knowledge.dto;

public record KnowledgeCitation(
        String documentId,
        String title,
        String sourceUrl,
        int chunkIndex,
        double score,
        String content
) {
}
