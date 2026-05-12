package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeChunkCreateRequest {
    private String content;
    private Integer index;
    private String chunkId;
}
