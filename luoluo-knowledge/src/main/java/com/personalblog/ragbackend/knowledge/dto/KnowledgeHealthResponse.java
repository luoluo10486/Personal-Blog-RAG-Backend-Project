package com.personalblog.ragbackend.knowledge.dto;

public record KnowledgeHealthResponse(
        boolean enabled,
        String defaultBaseCode,
        String vectorType,
        String collectionName,
        String embeddingModel,
        String chatModel
) {
}
