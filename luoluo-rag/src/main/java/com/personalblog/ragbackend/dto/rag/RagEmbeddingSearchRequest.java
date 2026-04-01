package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Embedding 检索演示请求。
 */
public record RagEmbeddingSearchRequest(
        @NotBlank(message = "query must not be blank")
        String query,
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must not exceed 20")
        Integer topK
) {
}
