package com.personalblog.ragbackend.knowledge.dto;

import java.util.List;

public record KnowledgeTrace(
        String route,
        String vectorType,
        String collectionName,
        int requestedTopK,
        List<String> steps
) {
}
