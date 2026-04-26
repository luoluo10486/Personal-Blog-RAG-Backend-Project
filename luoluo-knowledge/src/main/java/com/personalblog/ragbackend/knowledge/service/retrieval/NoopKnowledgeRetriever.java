package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnMissingBean(KnowledgeRetriever.class)
public class NoopKnowledgeRetriever implements KnowledgeRetriever {
    @Override
    public List<KnowledgeChunk> retrieve(String baseCode, String question, int topK) {
        return List.of();
    }
}
