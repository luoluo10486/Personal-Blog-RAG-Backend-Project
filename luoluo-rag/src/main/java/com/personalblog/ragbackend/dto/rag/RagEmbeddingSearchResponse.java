package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * Embedding 检索演示响应。
 */
public record RagEmbeddingSearchResponse(
        String query,
        String embeddingModel,
        int chunkCount,
        int vectorDimension,
        List<RagEmbeddingSearchResult> results
) {
}
