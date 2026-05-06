package com.personalblog.ragbackend.knowledge.dto;

import java.util.List;

public record KnowledgeTrace(
        String traceId,
        String conversationId,
        String route,
        String vectorType,
        String collectionName,
        int requestedTopK,
        String question,
        String rewrittenQuestion,
        List<String> steps
) {
}
