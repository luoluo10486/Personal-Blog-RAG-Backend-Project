package com.personalblog.ragbackend.dto.rag;

/**
 * RAG 评测汇总指标。
 */
public record RagEvaluationSummary(
        int totalCases,
        double intentAccuracy,
        double hitRate,
        double mrr,
        double avgFaithfulness,
        double avgRelevancy,
        double avgCorrectness,
        double correctRate,
        double fallbackRate,
        double hallucinationRate
) {
}
