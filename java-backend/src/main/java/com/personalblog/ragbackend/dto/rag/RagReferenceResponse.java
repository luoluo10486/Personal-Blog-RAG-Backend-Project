package com.personalblog.ragbackend.dto.rag;

public record RagReferenceResponse(
        String id,
        String title,
        double score
) {
}
