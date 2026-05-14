package com.personalblog.ragbackend.knowledge.service.ingest;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeIngestionService {
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;

    public KnowledgeIngestionService(KnowledgeIngestionEngine knowledgeIngestionEngine) {
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
    }

    public boolean isReady() {
        return true;
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
