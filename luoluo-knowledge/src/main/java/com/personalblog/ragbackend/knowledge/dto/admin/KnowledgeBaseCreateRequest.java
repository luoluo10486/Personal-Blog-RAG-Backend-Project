package com.personalblog.ragbackend.knowledge.dto.admin;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeBaseCreateRequest {
    @NotBlank(message = "knowledge base name must not be blank")
    private String name;
    private String embeddingModel;
    private String collectionName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}
