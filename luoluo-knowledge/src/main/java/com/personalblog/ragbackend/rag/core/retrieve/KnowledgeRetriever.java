package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;

import java.util.List;

public interface KnowledgeRetriever {
    List<RetrievedChunk> retrieve(String baseCode, String question, int topK);
}
