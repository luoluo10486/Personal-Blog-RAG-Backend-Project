package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeBaseCreateRequest {
    private String name;
    private String embeddingModel;
    private String collectionName;
}
