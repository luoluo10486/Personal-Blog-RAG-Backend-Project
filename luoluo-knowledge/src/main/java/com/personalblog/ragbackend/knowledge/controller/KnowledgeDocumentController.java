package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentChunkLogView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@MemberLoginRequired
public class KnowledgeDocumentController {
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeDocumentController(KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
    }

    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentView> upload(@PathVariable("kb-id") String kbId,
                                           @RequestPart(value = "file", required = false) MultipartFile file,
                                           @ModelAttribute KnowledgeDocumentUploadRequest request) {
        return R.ok(knowledgeAdminApplicationService.uploadDocument(kbId, request, file));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public R<Void> startChunk(@PathVariable("doc-id") String docId) {
        knowledgeAdminApplicationService.startChunk(docId);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public R<Void> delete(@PathVariable("doc-id") String docId) {
        knowledgeAdminApplicationService.deleteDocument(docId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}")
    public R<KnowledgeDocumentView> get(@PathVariable("doc-id") String docId) {
        return R.ok(knowledgeAdminApplicationService.getDocument(docId));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}")
    public R<Void> update(@PathVariable("doc-id") String docId,
                          @org.springframework.web.bind.annotation.RequestBody KnowledgeDocumentUpdateRequest request) {
        knowledgeAdminApplicationService.updateDocument(docId, request);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kb-id}/docs")
    public R<IPage<KnowledgeDocumentView>> page(@PathVariable("kb-id") String kbId,
                                                KnowledgeDocumentPageRequest request) {
        return R.ok(knowledgeAdminApplicationService.pageDocuments(kbId, request));
    }

    @GetMapping("/knowledge-base/docs/search")
    public R<List<KnowledgeDocumentSearchView>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return R.ok(knowledgeAdminApplicationService.searchDocuments(keyword, limit));
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/enable")
    public R<Void> enable(@PathVariable("doc-id") String docId,
                          @RequestParam("value") boolean enabled) {
        knowledgeAdminApplicationService.enableDocument(docId, enabled);
        return R.ok();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunk-logs")
    public R<IPage<KnowledgeDocumentChunkLogView>> getChunkLogs(@PathVariable("doc-id") String docId,
                                                                Page<KnowledgeDocumentChunkLogView> page) {
        return R.ok(knowledgeAdminApplicationService.pageChunkLogs(docId, page.getCurrent(), page.getSize()));
    }
}
