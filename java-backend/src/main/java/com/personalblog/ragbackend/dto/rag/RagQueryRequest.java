package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagQueryRequest(
        @NotBlank
        @Size(min = 2, max = 500)
        String question
) {
}
