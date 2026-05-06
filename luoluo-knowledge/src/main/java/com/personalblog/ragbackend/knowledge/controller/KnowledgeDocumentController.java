package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.application.KnowledgeDocumentApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/luoluo/knowledge/document", "/luoluo/knowledge"})
public class KnowledgeDocumentController {
    private final KnowledgeDocumentApplicationService knowledgeDocumentApplicationService;
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeDocumentController(KnowledgeDocumentApplicationService knowledgeDocumentApplicationService,
                                       KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeDocumentApplicationService = knowledgeDocumentApplicationService;
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
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
    public ResponseEntity<DocumentIngestionSummary> ingestDocument(@RequestPart("file") MultipartFile file,
                                                                   @RequestParam(value = "baseCode", required = false) String baseCode) {
        DocumentIngestionSummary result = knowledgeDocumentApplicationService.ingestFile(baseCode, file);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping(value = "/knowledge-base/{kbId}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentView> upload(@PathVariable Long kbId,
                                           @RequestPart(value = "file", required = false) MultipartFile file,
                                           @ModelAttribute KnowledgeDocumentUploadRequest request) {
        return R.ok(knowledgeAdminApplicationService.uploadDocument(kbId, request, file));
    }

    @PostMapping("/knowledge-base/docs/{docId}/chunk")
    public R<Void> startChunk(@PathVariable Long docId) {
        knowledgeAdminApplicationService.startChunk(docId);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/docs/{docId}")
    public R<Void> delete(@PathVariable Long docId) {
        knowledgeAdminApplicationService.deleteDocument(docId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/docs/{docId}")
    public R<KnowledgeDocumentView> get(@PathVariable Long docId) {
        return R.ok(knowledgeAdminApplicationService.getDocument(docId));
    }

    @PutMapping("/knowledge-base/docs/{docId}")
    public R<Void> update(@PathVariable Long docId,
                          @org.springframework.web.bind.annotation.RequestBody KnowledgeDocumentUpdateRequest request) {
        knowledgeAdminApplicationService.updateDocument(docId, request);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kbId}/docs")
    public R<IPage<KnowledgeDocumentView>> page(@PathVariable Long kbId,
                                                KnowledgeDocumentPageRequest request) {
        return R.ok(knowledgeAdminApplicationService.pageDocuments(kbId, request));
    }

    @GetMapping("/knowledge-base/docs/search")
    public R<List<KnowledgeDocumentSearchView>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return R.ok(knowledgeAdminApplicationService.searchDocuments(keyword, limit));
    }

    @PatchMapping("/knowledge-base/docs/{docId}/enable")
    public R<Void> enable(@PathVariable Long docId,
                          @RequestParam("value") boolean enabled) {
        knowledgeAdminApplicationService.enableDocument(docId, enabled);
        return R.ok();
    }
}
