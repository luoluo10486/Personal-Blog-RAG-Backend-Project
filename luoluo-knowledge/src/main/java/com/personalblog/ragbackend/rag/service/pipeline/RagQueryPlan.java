package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;

import java.util.List;

public record RagQueryPlan(
        String originalQuestion,
        RewriteResult rewriteResult,
        List<SubQuestionIntent> subIntents,
        IntentGroup intentGroup
) {
    public String rewrittenQuestion() {
        return rewriteResult == null ? "" : rewriteResult.rewrittenQuestion();
    }

    public List<String> subQuestions() {
        return rewriteResult == null ? List.of() : rewriteResult.subQuestions();
    }
}
