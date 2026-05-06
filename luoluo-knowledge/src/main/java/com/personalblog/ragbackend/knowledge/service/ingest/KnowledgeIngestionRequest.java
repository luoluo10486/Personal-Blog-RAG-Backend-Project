package com.personalblog.ragbackend.knowledge.service.ingest;

import org.springframework.web.multipart.MultipartFile;

public record KnowledgeIngestionRequest(
        String baseCode,
        MultipartFile file,
        KnowledgeIngestionMode mode,
        Long pipelineId,
        String taskId,
        String sourceType,
        String sourceLocation,
        String sourceFileName
) {
    public KnowledgeIngestionRequest(String baseCode, MultipartFile file) {
        this(baseCode, file, file == null ? KnowledgeIngestionMode.PLAN_ONLY : KnowledgeIngestionMode.PREVIEW);
    }

    public KnowledgeIngestionRequest(String baseCode, MultipartFile file, KnowledgeIngestionMode mode) {
        this(baseCode, file, mode, null, null, null, null, null);
    }
}
