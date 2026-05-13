package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentVO> upload(@PathVariable("kb-id") String kbId,
                                           @RequestPart(value = "file", required = false) MultipartFile file,
                                           @ModelAttribute KnowledgeDocumentUploadRequest requestParam) {
        return R.ok(knowledgeDocumentService.upload(kbId, requestParam, file));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public R<Void> startChunk(@PathVariable("doc-id") String docId) {
        knowledgeDocumentService.startChunk(docId);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public R<Void> delete(@PathVariable("doc-id") String docId) {
        knowledgeDocumentService.delete(docId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}")
    public R<KnowledgeDocumentVO> get(@PathVariable("doc-id") String docId) {
        return R.ok(knowledgeDocumentService.get(docId));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}")
    public R<Void> update(@PathVariable("doc-id") String docId,
                          @RequestBody KnowledgeDocumentUpdateRequest requestParam) {
        knowledgeDocumentService.update(docId, requestParam);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kb-id}/docs")
    public R<IPage<KnowledgeDocumentVO>> page(@PathVariable("kb-id") String kbId,
                                                KnowledgeDocumentPageRequest requestParam) {
        return R.ok(knowledgeDocumentService.page(kbId, requestParam));
    }

    @GetMapping("/knowledge-base/docs/search")
    public R<List<KnowledgeDocumentSearchVO>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return R.ok(knowledgeDocumentService.search(keyword, limit));
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/enable")
    public R<Void> enable(@PathVariable("doc-id") String docId,
                          @RequestParam("value") boolean enabled) {
        knowledgeDocumentService.enable(docId, enabled);
        return R.ok();
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunk-logs")
    public R<IPage<KnowledgeDocumentChunkLogVO>> getChunkLogs(@PathVariable("doc-id") String docId,
                                                              Page<KnowledgeDocumentChunkLogVO> page) {
        return R.ok(knowledgeDocumentService.getChunkLogs(docId, page));
    }
}
