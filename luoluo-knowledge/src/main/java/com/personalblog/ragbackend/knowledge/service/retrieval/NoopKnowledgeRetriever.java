package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoopKnowledgeRetriever implements KnowledgeRetriever, KnowledgeCandidateRetriever {
    @Override
    public String getName() {
        return "noop";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<KnowledgeChunk> retrieve(String baseCode, String question, int topK) {
        return retrieveCandidates(new RetrieveRequest(baseCode, question, topK));
    }

    @Override
    public List<KnowledgeChunk> retrieveCandidates(RetrieveRequest request) {
        return List.of();
    }
}
