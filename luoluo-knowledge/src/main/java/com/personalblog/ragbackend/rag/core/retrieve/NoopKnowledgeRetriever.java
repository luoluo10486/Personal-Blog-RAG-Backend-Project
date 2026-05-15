package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Deprecated(forRemoval = false)
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
    public List<RetrievedChunk> retrieve(String baseCode, String question, int topK) {
        return retrieveCandidates(new RetrieveRequest(baseCode, question, topK));
    }

    @Override
    public List<RetrievedChunk> retrieveCandidates(RetrieveRequest request) {
        return List.of();
    }
}
