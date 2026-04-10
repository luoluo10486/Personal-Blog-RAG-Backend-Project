package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * RAG 生成响应：返回答案正文、生成信息、检索摘要与引用列表。
 */
public record RagGenerationResponse(
        String query,
        String answer,
        String requestId,
        String model,
        String finishReason,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String embeddingModel,
        int retrievedChunkCount,
        String recallMode,
        boolean rerankApplied,
        String rerankProvider,
        String rerankModel,
        boolean functionCallApplied,
        List<String> calledTools,
        List<RagGenerationCitation> citations
) {
}
