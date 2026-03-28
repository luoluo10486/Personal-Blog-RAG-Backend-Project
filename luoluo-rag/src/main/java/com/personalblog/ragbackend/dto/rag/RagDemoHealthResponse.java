package com.personalblog.ragbackend.dto.rag;

public record RagDemoHealthResponse(
        boolean enabled,
        String apiUrl,
        String model
) {
}
