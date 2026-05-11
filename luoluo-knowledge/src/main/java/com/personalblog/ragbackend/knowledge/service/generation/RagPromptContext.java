package com.personalblog.ragbackend.knowledge.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;

import java.util.List;

public record RagPromptContext(
        String question,
        List<KnowledgeChunk> chunks,
        List<NodeScore> kbIntents,
        List<NodeScore> mcpIntents,
        String mcpContext,
        List<String> subQuestions
) {
    public boolean hasKb() {
        return CollUtil.isNotEmpty(chunks);
    }

    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    public boolean hasSubQuestions() {
        return CollUtil.isNotEmpty(subQuestions);
    }
}
