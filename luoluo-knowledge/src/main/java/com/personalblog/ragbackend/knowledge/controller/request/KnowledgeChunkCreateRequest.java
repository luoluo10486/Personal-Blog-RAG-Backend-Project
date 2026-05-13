package com.personalblog.ragbackend.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeChunkCreateRequest {
    private String content;
    private Integer index;
    private String chunkId;
}
