package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;

import java.util.List;
import java.util.Map;

public record RagPromptContext(
        String question,
        List<RetrievedChunk> chunks,
        List<NodeScore> kbIntents,
        List<NodeScore> mcpIntents,
        String mcpContext,
        List<String> subQuestions,
        String kbContext,
        Map<String, List<RetrievedChunk>> intentChunks
) {
    public RagPromptContext(String question,
                            List<RetrievedChunk> chunks,
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
