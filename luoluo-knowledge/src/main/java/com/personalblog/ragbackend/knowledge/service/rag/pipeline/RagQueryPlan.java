package com.personalblog.ragbackend.knowledge.service.rag.pipeline;

import com.personalblog.ragbackend.knowledge.service.rag.intent.GuidanceDecision;
import com.personalblog.ragbackend.knowledge.service.rag.intent.IntentGroup;
import com.personalblog.ragbackend.knowledge.service.rag.intent.SubQuestionIntent;

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
