package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;

import java.util.List;

public interface SearchResultPostProcessor {

    String getName();

    int getOrder();

    default boolean isEnabled(RetrieveRequest request) {
        return true;
    }

    List<RetrievedChunk> process(List<RetrievedChunk> chunks, RetrieveRequest request);
}
