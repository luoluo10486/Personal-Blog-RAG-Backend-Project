package com.personalblog.ragbackend.knowledge.dto.admin;

import java.util.List;

public class KnowledgeChunkBatchRequest {
    private List<String> chunkIds;

    public List<String> getChunkIds() {
        return chunkIds;
    }

    public void setChunkIds(List<String> chunkIds) {
        this.chunkIds = chunkIds;
    }
}
