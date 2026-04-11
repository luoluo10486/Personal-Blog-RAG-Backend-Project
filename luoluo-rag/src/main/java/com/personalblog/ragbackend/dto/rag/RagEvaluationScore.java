package com.personalblog.ragbackend.dto.rag;

/**
 * 单个评估维度评分。
 */
public record RagEvaluationScore(
        int score,
        String label,
        String reason
) {
}
