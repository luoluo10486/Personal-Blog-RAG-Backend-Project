package com.personalblog.ragbackend.knowledge.service.retrieval;

public record RetrieveRequest(
        String baseCode,
        String question,
        int topK
) {
}
