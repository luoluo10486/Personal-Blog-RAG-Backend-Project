package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestionService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;

    public KnowledgeIngestionService(KnowledgeProperties knowledgeProperties,
                                     KnowledgeVectorSpaceResolver vectorSpaceResolver) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
    }

    public boolean isReady() {
        return knowledgeProperties.isEnabled();
    }

    public KnowledgeIngestionPlan plan(String baseCode) {
        String normalizedBaseCode = vectorSpaceResolver.normalizeBaseCode(baseCode);
        return new KnowledgeIngestionPlan(
                normalizedBaseCode,
                vectorSpaceResolver.resolve(normalizedBaseCode),
                knowledgeProperties.getChunking().getChunkSize(),
                knowledgeProperties.getChunking().getChunkOverlap(),
                knowledgeProperties.getChunking().getMaxChunkCount()
        );
    }
}
