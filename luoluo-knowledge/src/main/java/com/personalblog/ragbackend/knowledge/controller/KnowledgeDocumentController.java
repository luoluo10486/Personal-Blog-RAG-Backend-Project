package com.personalblog.ragbackend.knowledge.controller;

import com.personalblog.ragbackend.knowledge.application.KnowledgeDocumentApplicationService;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/luoluo/knowledge/document", "/luoluo/rag/document"})
public class KnowledgeDocumentController {
    private final KnowledgeDocumentApplicationService knowledgeDocumentApplicationService;

    public KnowledgeDocumentController(KnowledgeDocumentApplicationService knowledgeDocumentApplicationService) {
        this.knowledgeDocumentApplicationService = knowledgeDocumentApplicationService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseDocument(@RequestPart("file") MultipartFile file) {
        ParseResult result = knowledgeDocumentApplicationService.parseFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentChunkResponse> chunkDocument(@RequestPart("file") MultipartFile file) {
        DocumentChunkResponse result = knowledgeDocumentApplicationService.chunkFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentIngestionSummary> ingestDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "baseCode", required = false) String baseCode
    ) {
        DocumentIngestionSummary result = knowledgeDocumentApplicationService.ingestFile(baseCode, file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
