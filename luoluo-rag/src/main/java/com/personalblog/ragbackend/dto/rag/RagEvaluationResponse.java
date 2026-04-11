package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * RAG 评测响应。
 */
public record RagEvaluationResponse(
        RagEvaluationSummary summary,
        List<RagEvaluationCaseResult> caseResults
) {
    public RagEvaluationResponse {
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
    }
}
