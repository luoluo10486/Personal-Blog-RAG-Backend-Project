package com.personalblog.ragbackend.knowledge.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;

import java.util.List;
import java.util.Map;

public record RagPromptContext(
        String question,
        List<KnowledgeChunk> chunks,
        List<NodeScore> kbIntents,
        List<NodeScore> mcpIntents,
        String mcpContext,
        List<String> subQuestions,
        String kbContext,
        Map<String, List<KnowledgeChunk>> intentChunks
) {
    public RagPromptContext(String question,
                            List<KnowledgeChunk> chunks,
                            List<NodeScore> kbIntents,
                            List<NodeScore> mcpIntents,
                            String mcpContext,
                            List<String> subQuestions) {
        this(question, chunks, kbIntents, mcpIntents, mcpContext, subQuestions, "", Map.of());
    }

    public boolean hasKb() {
        return CollUtil.isNotEmpty(chunks) || StrUtil.isNotBlank(kbContext);
    }

    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    public boolean hasSubQuestions() {
        return CollUtil.isNotEmpty(subQuestions);
    }
}
