package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.List;

public interface RetrieverService {
    default List<KnowledgeChunk> retrieve(String query, int topK) {
        return retrieve(RetrieveRequest.builder().query(query).topK(topK).build());
    }

    List<KnowledgeChunk> retrieve(RetrieveRequest retrieveParam);

    List<KnowledgeChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam);
}
