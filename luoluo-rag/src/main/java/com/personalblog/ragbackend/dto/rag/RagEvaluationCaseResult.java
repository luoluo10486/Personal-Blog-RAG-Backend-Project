package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * 单条评测结果。
 */
public record RagEvaluationCaseResult(
        String query,
        String expectedIntent,
        String predictedIntent,
        boolean routeMatched,
        List<String> retrievedDocIds,
        boolean hit,
        double reciprocalRank,
        String actualAnswer,
        boolean functionCallApplied,
        List<String> calledTools,
        RagEvaluationScore faithfulness,
        RagEvaluationScore relevancy,
        RagEvaluationScore correctness,
        String rootCause
) {
    public RagEvaluationCaseResult {
        retrievedDocIds = retrievedDocIds == null ? List.of() : List.copyOf(retrievedDocIds);
        calledTools = calledTools == null ? List.of() : List.copyOf(calledTools);
    }
}
