package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
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
        return runIngestion(file).parseResult();
    }

    public DocumentChunkResponse chunkFile(MultipartFile file) {
        return runIngestion(file).chunkResponse();
    }

    public DocumentChunkResponse chunkText(String content) {
        return knowledgeDocumentChunkService.chunkText(content);
    }

    private KnowledgeIngestionResult runIngestion(MultipartFile file) {
        return knowledgeIngestionEngine.execute(new KnowledgeIngestionRequest(null, file));
    }
}
