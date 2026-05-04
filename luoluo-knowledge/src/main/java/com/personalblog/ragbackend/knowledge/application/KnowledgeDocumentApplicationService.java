package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocumentApplicationService {
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;

    public KnowledgeDocumentApplicationService(KnowledgeIngestionEngine knowledgeIngestionEngine,
                                               KnowledgeDocumentChunkService knowledgeDocumentChunkService) {
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
    }

    public ParseResult parseFile(MultipartFile file) {
        return runPreview(file).parseResult();
    }

    public DocumentChunkResponse chunkFile(MultipartFile file) {
        return runPreview(file).chunkResponse();
    }

    public DocumentChunkResponse chunkText(String content) {
        return knowledgeDocumentChunkService.chunkText(content);
    }

    public DocumentIngestionSummary ingestFile(String baseCode, MultipartFile file) {
        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(baseCode, file, KnowledgeIngestionMode.INGEST)
        );
        if (result.ingestionSummary() != null) {
            return result.ingestionSummary();
        }
        return DocumentIngestionSummary.failure(baseCode, "Ingestion did not produce a summary");
    }

    private KnowledgeIngestionResult runPreview(MultipartFile file) {
        return knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(null, file, KnowledgeIngestionMode.PREVIEW)
        );
    }
}
