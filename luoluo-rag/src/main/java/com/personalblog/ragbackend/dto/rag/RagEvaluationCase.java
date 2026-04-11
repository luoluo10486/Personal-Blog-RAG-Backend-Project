package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * 单条 RAG 评测样本。
 */
public record RagEvaluationCase(
        String query,
        String expectedAnswer,
        List<String> relevantDocIds,
        String expectedIntent
) {
    public RagEvaluationCase {
        relevantDocIds = relevantDocIds == null ? List.of() : List.copyOf(relevantDocIds);
    }
}
