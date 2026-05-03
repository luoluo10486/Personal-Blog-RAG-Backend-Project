package com.personalblog.ragbackend.knowledge.controller;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.document.TikaDocumentParseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/luoluo/knowledge/document", "/luoluo/rag/document"})
public class KnowledgeDocumentController {
    private final TikaDocumentParseService tikaDocumentParseService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;

    public KnowledgeDocumentController(TikaDocumentParseService tikaDocumentParseService,
                                       KnowledgeDocumentChunkService knowledgeDocumentChunkService) {
        this.tikaDocumentParseService = tikaDocumentParseService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseDocument(@RequestPart("file") MultipartFile file) {
        ParseResult result = tikaDocumentParseService.parseFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentChunkResponse> chunkDocument(@RequestPart("file") MultipartFile file) {
        DocumentChunkResponse result = knowledgeDocumentChunkService.chunkFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
