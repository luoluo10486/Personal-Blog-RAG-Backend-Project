package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;

import java.util.List;

public interface KnowledgeCandidateRetriever {

    String getName();

    default int getOrder() {
        return 100;
    }

    default boolean isEnabled(RetrieveRequest request) {
        return true;
    }

    List<RetrievedChunk> retrieveCandidates(RetrieveRequest request);
}
