package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.dto.document.ParseResult;
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

    public DocumentController(TikaParseService tikaParseService) {
        this.tikaParseService = tikaParseService;
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
}
