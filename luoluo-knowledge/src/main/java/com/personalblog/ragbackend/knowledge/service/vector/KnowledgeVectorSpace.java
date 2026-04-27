package com.personalblog.ragbackend.knowledge.service.vector;

public record KnowledgeVectorSpace(
        KnowledgeVectorSpaceId spaceId,
        String collectionName,
        String vectorType,
        String embeddingModel,
        int dimension
) {
}
