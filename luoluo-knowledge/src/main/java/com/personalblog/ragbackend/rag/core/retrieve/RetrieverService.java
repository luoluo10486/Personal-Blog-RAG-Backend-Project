package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;

import java.util.List;

public interface RetrieverService {
    default List<RetrievedChunk> retrieve(String query, int topK) {
        return retrieve(RetrieveRequest.builder().query(query).topK(topK).build());
    }

    List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam);

    List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam);
}
