package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.rag.core.guidance.GuidanceDecision;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.List;

public record RagQueryPlan(
        String originalQuestion,
        String rewrittenQuestion,
        int topK,
        List<SubQuestionIntent> subIntents,
        IntentGroup intentGroup,
        GuidanceDecision guidanceDecision,
        String directAnswer,
        List<String> steps
) {
    public boolean hasGuidancePrompt() {
        return guidanceDecision != null && guidanceDecision.isPrompt();
    }

    public boolean hasDirectAnswer() {
        return directAnswer != null && !directAnswer.isBlank();
    }

    public List<String> subQuestions() {
        if (CollUtil.isEmpty(subIntents)) {
            return List.of();
        }
        return subIntents.stream()
                .map(SubQuestionIntent::subQuestion)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
