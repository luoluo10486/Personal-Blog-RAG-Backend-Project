package com.personalblog.ragbackend.dto.rag;

/**
 * RAG 模块健康检查响应，返回开关状态和当前模型配置摘要。
 */
public record RagDemoHealthResponse(
        boolean enabled,
        String apiUrl,
        String model,
        String embeddingApiUrl,
        String embeddingModel,
        String embeddingProvider,
        boolean milvusEnabled
) {
}
