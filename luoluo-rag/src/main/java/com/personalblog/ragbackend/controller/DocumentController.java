package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.dto.document.ParseResult;
import com.personalblog.ragbackend.service.DocumentChunkService;
import com.personalblog.ragbackend.service.TikaParseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析控制器，提供基于 Tika 的文件正文提取能力。
 */
@RestController
@RequestMapping("/luoluo/rag/document")
public class DocumentController {
    private final TikaParseService tikaParseService;
    private final DocumentChunkService documentChunkService;

    public DocumentController(TikaParseService tikaParseService, DocumentChunkService documentChunkService) {
        this.tikaParseService = tikaParseService;
        this.documentChunkService = documentChunkService;
    }

    /**
     * 解析上传文件，返回正文、MIME 类型和元数据。
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseDocument(@RequestPart("file") MultipartFile file) {
        ParseResult result = tikaParseService.parseFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * Parse and chunk an uploaded document for downstream embedding and retrieval tests.
     */
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentChunkResponse> chunkDocument(@RequestPart("file") MultipartFile file) {
        DocumentChunkResponse result = documentChunkService.chunkFile(file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
