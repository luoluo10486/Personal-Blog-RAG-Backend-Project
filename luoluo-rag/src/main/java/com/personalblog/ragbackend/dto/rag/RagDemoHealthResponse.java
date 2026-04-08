package com.personalblog.ragbackend.dto.rag;

/**
 * RAG 演示模块健康检查响应，返回关键开关与配置摘要。
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
                "HYBRID", false, "未启用", "");
    }
}
