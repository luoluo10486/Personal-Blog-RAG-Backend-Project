package com.personalblog.ragbackend.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {
    private String id;
    private String name;
    private String embeddingModel;
}
