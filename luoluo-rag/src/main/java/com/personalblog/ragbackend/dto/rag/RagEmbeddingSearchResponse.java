package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * 检索响应：返回召回模式、是否重排，以及最终结果列表。
 */
public record RagEmbeddingSearchResponse(
        String query,
        String embeddingModel,
        int chunkCount,
        int vectorDimension,
        String recallMode,
        int recallCount,
        boolean rerankApplied,
        String rerankProvider,
        String rerankModel,
        List<RagEmbeddingSearchResult> results
) {
    public RagEmbeddingSearchResponse(
            String query,
            String embeddingModel,
            int chunkCount,
            int vectorDimension,
            List<RagEmbeddingSearchResult> results
    ) {
        this(query, embeddingModel, chunkCount, vectorDimension, "DENSE_ONLY",
                results == null ? 0 : results.size(), false, "未启用", "", results);
    }
}
