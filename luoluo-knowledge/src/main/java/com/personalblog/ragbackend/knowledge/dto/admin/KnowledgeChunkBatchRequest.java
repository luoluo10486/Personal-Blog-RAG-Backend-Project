package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeChunkBatchRequest {
    private List<String> chunkIds;
}
