package com.personalblog.ragbackend.dto.rag;

import java.util.Map;

/**
 * Embedding 检索命中项。
 */
public record RagEmbeddingSearchResult(
        int rank,
        double similarity,
        String content,
        Map<String, String> metadata
) {
}
