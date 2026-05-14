package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RetrievalContext {
    private String mcpContext;
    private String kbContext;
    private Map<String, List<RetrievedChunk>> intentChunks;

    public static RetrievalContext empty() {
        return RetrievalContext.builder()
                .mcpContext("")
                .kbContext("")
                .intentChunks(Map.of())
                .build();
    }

    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }

    public boolean isEmpty() {
        return !hasMcp() && !hasKb();
    }
}
