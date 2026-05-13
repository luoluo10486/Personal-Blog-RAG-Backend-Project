package com.personalblog.ragbackend.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeBaseCreateRequest {
    private String name;
    private String embeddingModel;
    private String collectionName;
}
