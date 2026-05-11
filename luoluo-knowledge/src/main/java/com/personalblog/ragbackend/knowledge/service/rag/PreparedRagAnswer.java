package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.rag.intent.IntentGroup;
import com.personalblog.ragbackend.knowledge.service.rag.pipeline.RagQueryPlan;

import java.util.List;

public record PreparedRagAnswer(
        String originalQuestion,
        String rewrittenQuestion,
        String baseCode,
        int topK,
        List<ChatMessage> memory,
        RagQueryPlan plan,
        IntentGroup intentGroup,
        List<KnowledgeChunk> chunks,
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
}
