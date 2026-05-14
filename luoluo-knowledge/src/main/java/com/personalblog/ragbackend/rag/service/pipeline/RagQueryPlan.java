package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.rag.core.guidance.GuidanceDecision;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;

import java.util.List;

public record RagQueryPlan(
        String originalQuestion,
        String rewrittenQuestion,
        String baseCode,
        int topK,
        List<SubQuestionIntent> subIntents,
        IntentGroup intentGroup,
        GuidanceDecision guidanceDecision,
        String directAnswer,
        List<String> steps
) {
    public boolean hasDirectAnswer() {
        return directAnswer != null && !directAnswer.isBlank();
    }
}
