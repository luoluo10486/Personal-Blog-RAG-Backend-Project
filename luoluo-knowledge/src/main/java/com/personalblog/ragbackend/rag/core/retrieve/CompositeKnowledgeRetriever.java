package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class CompositeKnowledgeRetriever implements KnowledgeRetriever {
    private final KnowledgeRetrievalEngine knowledgeRetrievalEngine;

    public CompositeKnowledgeRetriever(KnowledgeRetrievalEngine knowledgeRetrievalEngine) {
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
    }

    @Override
    public List<RetrievedChunk> retrieve(String baseCode, String question, int topK) {
        return knowledgeRetrievalEngine.retrieve(new RetrieveRequest(baseCode, question, topK));
    }
}
