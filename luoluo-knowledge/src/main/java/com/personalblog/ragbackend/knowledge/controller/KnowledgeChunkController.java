package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@MemberLoginRequired
public class KnowledgeChunkController {
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeChunkController(KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
    }

    @GetMapping("/knowledge-base/docs/{doc-id}/chunks")
    public R<IPage<KnowledgeChunkVO>> pageQuery(@PathVariable("doc-id") String docId,
                                                @Validated KnowledgeChunkPageRequest requestParam) {
        return R.ok(knowledgeAdminApplicationService.pageChunks(docId, requestParam));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunks")
    public R<KnowledgeChunkVO> create(@PathVariable("doc-id") String docId,
                                      @RequestBody KnowledgeChunkCreateRequest request) {
        return R.ok(knowledgeAdminApplicationService.createChunk(docId, request));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public R<Void> update(@PathVariable("doc-id") String docId,
                          @PathVariable("chunk-id") String chunkId,
                          @RequestBody KnowledgeChunkUpdateRequest request) {
        knowledgeAdminApplicationService.updateChunk(docId, chunkId, request);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public R<Void> delete(@PathVariable("doc-id") String docId,
                          @PathVariable("chunk-id") String chunkId) {
        knowledgeAdminApplicationService.deleteChunk(docId, chunkId);
        return R.ok();
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable")
    public R<Void> enable(@PathVariable("doc-id") String docId,
                          @PathVariable("chunk-id") String chunkId,
                          @RequestParam("value") boolean enabled) {
        knowledgeAdminApplicationService.enableChunk(docId, chunkId, enabled);
        return R.ok();
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/batch-enable")
    public R<Void> batchEnable(@PathVariable("doc-id") String docId,
                               @RequestParam("value") boolean enabled,
                               @RequestBody(required = false) KnowledgeChunkBatchRequest request) {
        knowledgeAdminApplicationService.batchEnableChunks(docId, request, enabled);
        return R.ok();
    }
}
