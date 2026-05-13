package com.personalblog.ragbackend.rag.core.retrieve;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrieveRequest {
    private String query;
    @Builder.Default
    private int topK = 5;
    private String collectionName;
    private Map<String, Object> metadataFilters;

    public RetrieveRequest(String baseCode, String question, int topK) {
        this.query = question;
        this.topK = topK;
        this.collectionName = baseCode;
    }

    public String baseCode() {
        return collectionName;
    }

    public String question() {
        return query;
    }

    public int topK() {
        return topK;
    }

    public String collectionName() {
        return collectionName;
    }
}
