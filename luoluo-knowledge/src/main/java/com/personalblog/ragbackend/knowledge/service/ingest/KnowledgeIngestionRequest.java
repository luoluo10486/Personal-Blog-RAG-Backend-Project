package com.personalblog.ragbackend.knowledge.service.ingest;

import org.springframework.web.multipart.MultipartFile;

public record KnowledgeIngestionRequest(
        String baseCode,
        MultipartFile file
) {
}
