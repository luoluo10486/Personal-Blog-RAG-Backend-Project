package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.NotBlank;

public record RagDemoChatRequest(
        String systemPrompt,
        @NotBlank(message = "message must not be blank")
        String message
) {
}
