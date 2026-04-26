package com.personalblog.ragbackend.knowledge.domain;

public record KnowledgeChunk(
        String id,
        String baseCode,
        String documentId,
        String title,
        String sourceUrl,
        int chunkIndex,
        String content,
        double score
) {
}
