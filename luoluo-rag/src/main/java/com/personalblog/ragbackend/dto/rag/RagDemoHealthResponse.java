package com.personalblog.ragbackend.dto.rag;

/**
 * Health summary for the RAG demo module.
 */
public record RagDemoHealthResponse(
        boolean enabled,
        String apiUrl,
        String model,
        String embeddingApiUrl,
        String embeddingModel,
        String embeddingProvider,
        boolean milvusEnabled,
        String retrievalMode,
        boolean rerankEnabled,
        String rerankProvider,
        String rerankModel
) {
    public RagDemoHealthResponse(
            boolean enabled,
            String apiUrl,
            String model,
            String embeddingApiUrl,
            String embeddingModel,
            String embeddingProvider,
            boolean milvusEnabled
    ) {
        this(enabled, apiUrl, model, embeddingApiUrl, embeddingModel, embeddingProvider, milvusEnabled,
                "HYBRID", false, "disabled", "");
    }
}
