package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * RAG 评测请求。
 */
public record RagEvaluationRequest(
        List<RagEvaluationCase> cases,
        Boolean runJudge,
        Integer topK
) {
    public RagEvaluationRequest {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
