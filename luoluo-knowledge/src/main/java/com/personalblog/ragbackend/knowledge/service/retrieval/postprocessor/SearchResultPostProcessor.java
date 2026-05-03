package com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;

import java.util.List;

public interface SearchResultPostProcessor {

    String getName();

    int getOrder();

    default boolean isEnabled(RetrieveRequest request) {
        return true;
    }

    List<KnowledgeChunk> process(List<KnowledgeChunk> chunks, RetrieveRequest request);
}
