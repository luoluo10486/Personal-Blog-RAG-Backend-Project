package com.personalblog.ragbackend.knowledge.service.ingest;

import org.springframework.web.multipart.MultipartFile;

public record KnowledgeIngestionRequest(
        String baseCode,
        MultipartFile file,
        KnowledgeIngestionMode mode
) {
    public KnowledgeIngestionRequest(String baseCode, MultipartFile file) {
        this(baseCode, file, file == null ? KnowledgeIngestionMode.PLAN_ONLY : KnowledgeIngestionMode.PREVIEW);
    }
}
