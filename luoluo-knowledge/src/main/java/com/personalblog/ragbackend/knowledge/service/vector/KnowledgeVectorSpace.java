package com.personalblog.ragbackend.knowledge.service.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeVectorSpace {

    private KnowledgeVectorSpaceId spaceId;

    private String collectionName;

    private String vectorType;

    private String embeddingModel;

    private int dimension;

    public KnowledgeVectorSpaceId spaceId() {
        return spaceId;
    }

    public String collectionName() {
        return collectionName;
    }

    public String vectorType() {
        return vectorType;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int dimension() {
        return dimension;
    }
}
