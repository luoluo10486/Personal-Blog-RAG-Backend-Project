package com.personalblog.ragbackend.knowledge.service.vector.model;

import java.util.List;
import java.util.Map;

public record KnowledgeVectorDocument(
        String vectorId,
        String content,
        List<Float> embedding,
        Map<String, Object> metadata
) {
    public KnowledgeVectorDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
