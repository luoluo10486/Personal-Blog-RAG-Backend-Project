package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeBaseView(
        Long id,
        String name,
        String description,
        String embeddingModel,
        String collectionName,
        String visibility,
        String status,
        long documentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
