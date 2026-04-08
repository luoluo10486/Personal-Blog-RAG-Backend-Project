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
 * 文档处理控制器，提供基于 Tika 的正文解析，以及基于结构化规则的分块（chunk）能力。
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
     * 解析上传的文档，返回正文内容、MIME 类型和元数据。
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
     * 解析并分块上传的文档，方便后续做 embedding、入库与检索联调。
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
