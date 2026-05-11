package com.personalblog.ragbackend.knowledge.service.retrieval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RetrievalContext(
        String mcpContext,
        String kbContext,
        Map<String, List<KnowledgeChunk>> intentChunks
) {
    public RetrievalContext {
        mcpContext = StrUtil.blankToDefault(mcpContext, "");
        kbContext = StrUtil.blankToDefault(kbContext, "");
        intentChunks = intentChunks == null ? Map.of() : Map.copyOf(intentChunks);
    }

    public static RetrievalContext empty() {
        return new RetrievalContext("", "", Map.of());
    }

    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext) || intentChunks.values().stream().anyMatch(CollUtil::isNotEmpty);
    }

    public boolean isEmpty() {
        return !hasMcp() && !hasKb();
    }

    public List<KnowledgeChunk> allChunks() {
        if (intentChunks.isEmpty()) {
            return List.of();
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        intentChunks.values().forEach(value -> {
            if (CollUtil.isNotEmpty(value)) {
                chunks.addAll(value);
            }
        });
        return chunks.stream()
                .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.id()))
                .collect(java.util.stream.Collectors.toMap(
                        KnowledgeChunk::id,
                        chunk -> chunk,
                        (left, ignored) -> left,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }
}
