package com.personalblog.ragbackend.rag.core.retrieve;

public record RetrieveRequest(
        String baseCode,
        String question,
        int topK
) {
}
