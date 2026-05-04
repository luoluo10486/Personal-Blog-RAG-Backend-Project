package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeIngestionService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;

    public KnowledgeIngestionService(KnowledgeProperties knowledgeProperties,
                                     KnowledgeIngestionEngine knowledgeIngestionEngine) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
    }

    public boolean isReady() {
        return knowledgeProperties.isEnabled();
    }

    public KnowledgeIngestionPlan plan(String baseCode) {
        return knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(baseCode, null, KnowledgeIngestionMode.PLAN_ONLY)
        ).plan();
    }

    public KnowledgeIngestionResult ingest(String baseCode, MultipartFile file) {
        return knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(baseCode, file, KnowledgeIngestionMode.INGEST)
        );
    }
}
