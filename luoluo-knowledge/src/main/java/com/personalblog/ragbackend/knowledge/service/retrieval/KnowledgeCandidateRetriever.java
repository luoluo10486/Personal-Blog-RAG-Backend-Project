package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.List;

public interface KnowledgeCandidateRetriever {

    String getName();

    default int getOrder() {
        return 100;
    }

    default boolean isEnabled(RetrieveRequest request) {
        return true;
    }

    List<KnowledgeChunk> retrieveCandidates(RetrieveRequest request);
}
