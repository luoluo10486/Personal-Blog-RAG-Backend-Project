package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {
    private String id;
    private String name;
    private String embeddingModel;
}
