package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Validated
@MemberLoginRequired
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService knowledgeDocumentService;

    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentVO> upload(@PathVariable("kb-id") String kbId,
                                              @RequestPart(value = "file", required = false) MultipartFile file,
                                              @ModelAttribute KnowledgeDocumentUploadRequest requestParam) {
        return Results.success(knowledgeDocumentService.upload(kbId, requestParam, file));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public Result<Void> startChunk(@PathVariable("doc-id") String docId) {
        knowledgeDocumentService.startChunk(docId);
        return Results.success();
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public Result<Void> delete(@PathVariable("doc-id") String docId) {
        knowledgeDocumentService.delete(docId);
        return Results.success();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}")
    public Result<KnowledgeDocumentVO> get(@PathVariable("doc-id") String docId) {
        return Results.success(knowledgeDocumentService.get(docId));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}")
    public Result<Void> update(@PathVariable("doc-id") String docId,
                               @RequestBody KnowledgeDocumentUpdateRequest requestParam) {
        knowledgeDocumentService.update(docId, requestParam);
        return Results.success();
    }

    @GetMapping("/knowledge-base/{kb-id}/docs")
    public Result<IPage<KnowledgeDocumentVO>> page(@PathVariable("kb-id") String kbId,
                                                   KnowledgeDocumentPageRequest requestParam) {
        return Results.success(knowledgeDocumentService.page(kbId, requestParam));
    }

    @GetMapping("/knowledge-base/docs/search")
    public Result<List<KnowledgeDocumentSearchVO>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return Results.success(knowledgeDocumentService.search(keyword, limit));
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/enable")
    public Result<Void> enable(@PathVariable("doc-id") String docId,
                               @RequestParam("value") boolean enabled) {
        knowledgeDocumentService.enable(docId, enabled);
        return Results.success();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunk-logs")
    public Result<IPage<KnowledgeDocumentChunkLogVO>> getChunkLogs(@PathVariable("doc-id") String docId,
                                                                   Page<KnowledgeDocumentChunkLogVO> page) {
        return Results.success(knowledgeDocumentService.getChunkLogs(docId, page));
    }
}
