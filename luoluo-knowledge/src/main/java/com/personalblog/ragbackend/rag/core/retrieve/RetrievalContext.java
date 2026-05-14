package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RetrievalContext(
        String mcpContext,
        String kbContext,
        Map<String, List<RetrievedChunk>> intentChunks
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

    public List<RetrievedChunk> allChunks() {
        if (intentChunks.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> chunks = new ArrayList<>();
        intentChunks.values().forEach(value -> {
            if (CollUtil.isNotEmpty(value)) {
                chunks.addAll(value);
            }
        });
        return chunks.stream()
                .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getId()))
                .collect(java.util.stream.Collectors.toMap(
                        RetrievedChunk::getId,
                        chunk -> chunk,
                        (left, ignored) -> left,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }
}
