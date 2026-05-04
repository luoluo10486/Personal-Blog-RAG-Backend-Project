package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
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
    public List<KnowledgeChunk> retrieve(String baseCode, String question, int topK) {
        return knowledgeRetrievalEngine.retrieve(new RetrieveRequest(baseCode, question, topK));
    }
}
