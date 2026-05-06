package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkView;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/luoluo/knowledge")
public class KnowledgeChunkController {
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeChunkController(KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
    }

    @GetMapping("/knowledge-base/docs/{docId}/chunks")
    public R<IPage<KnowledgeChunkView>> pageChunks(@PathVariable Long docId,
                                                   KnowledgeChunkPageRequest request) {
        return R.ok(knowledgeAdminApplicationService.pageChunks(docId, request));
    }

    @PostMapping("/knowledge-base/docs/{docId}/chunks")
    public R<KnowledgeChunkView> createChunk(@PathVariable Long docId,
                                             @Valid @RequestBody KnowledgeChunkCreateRequest request) {
        return R.ok(knowledgeAdminApplicationService.createChunk(docId, request));
    }

    @PutMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}")
    public R<Void> updateChunk(@PathVariable Long docId,
                               @PathVariable Long chunkId,
                               @Valid @RequestBody KnowledgeChunkUpdateRequest request) {
        knowledgeAdminApplicationService.updateChunk(docId, chunkId, request);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}")
    public R<Void> deleteChunk(@PathVariable Long docId,
                               @PathVariable Long chunkId) {
        knowledgeAdminApplicationService.deleteChunk(docId, chunkId);
        return R.ok();
    }

    @PatchMapping("/knowledge-base/docs/{docId}/chunks/{chunkId}/enable")
    public R<Void> enableChunk(@PathVariable Long docId,
                               @PathVariable Long chunkId,
                               @RequestParam("value") boolean enabled) {
        knowledgeAdminApplicationService.enableChunk(docId, chunkId, enabled);
        return R.ok();
    }

    @PatchMapping("/knowledge-base/docs/{docId}/chunks/batch-enable")
    public R<Void> batchEnableChunks(@PathVariable Long docId,
                                     @RequestParam("value") boolean enabled,
                                     @RequestBody(required = false) KnowledgeChunkBatchRequest request) {
        knowledgeAdminApplicationService.batchEnableChunks(docId, request, enabled);
        return R.ok();
    }
}
