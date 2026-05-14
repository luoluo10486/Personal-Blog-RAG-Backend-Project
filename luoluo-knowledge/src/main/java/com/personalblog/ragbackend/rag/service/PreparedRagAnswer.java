package com.personalblog.ragbackend.rag.service;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.service.pipeline.RagQueryPlan;

import java.util.List;

public record PreparedRagAnswer(
        String originalQuestion,
        String rewrittenQuestion,
        String baseCode,
        int topK,
        List<ChatMessage> memory,
        RagQueryPlan plan,
        IntentGroup intentGroup,
        List<RetrievedChunk> chunks,
        List<KnowledgeCitation> citations,
        String mcpContext,
        KnowledgeTrace trace
) {
    public boolean hasDirectAnswer() {
        return plan != null && plan.hasDirectAnswer();
    }

    public String directAnswer() {
        return plan == null ? null : plan.directAnswer();
    }

    public boolean hasEvidence() {
        return (chunks != null && !chunks.isEmpty()) || StrUtil.isNotBlank(mcpContext);
    }

    public List<String> subQuestions() {
        if (plan == null || plan.subIntents() == null) {
            return List.of();
        }
        return plan.subIntents().stream()
                .map(SubQuestionIntent::subQuestion)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
