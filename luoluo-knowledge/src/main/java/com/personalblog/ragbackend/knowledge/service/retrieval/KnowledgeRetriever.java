package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.List;

public interface KnowledgeRetriever {
    List<KnowledgeChunk> retrieve(String baseCode, String question, int topK);
}
