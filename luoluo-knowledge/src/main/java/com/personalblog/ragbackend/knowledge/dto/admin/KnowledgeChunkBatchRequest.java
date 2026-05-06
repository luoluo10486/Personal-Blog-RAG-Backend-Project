package com.personalblog.ragbackend.knowledge.dto.admin;

import java.util.List;

public class KnowledgeChunkBatchRequest {
    private List<Long> chunkIds;

    public List<Long> getChunkIds() {
        return chunkIds;
    }

    public void setChunkIds(List<Long> chunkIds) {
        this.chunkIds = chunkIds;
    }
}
