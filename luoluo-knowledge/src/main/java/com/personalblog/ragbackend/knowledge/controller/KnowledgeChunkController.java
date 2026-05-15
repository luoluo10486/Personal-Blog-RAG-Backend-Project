package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KnowledgeChunkController {
    private final KnowledgeChunkService knowledgeChunkService;

    @GetMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<IPage<KnowledgeChunkVO>> pageQuery(@PathVariable("doc-id") String docId,
                                                     @Validated KnowledgeChunkPageRequest requestParam) {
        return Results.success(knowledgeChunkService.pageQuery(docId, requestParam));
    }

    @PostMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<KnowledgeChunkVO> create(@PathVariable("doc-id") String docId,
                                           @RequestBody KnowledgeChunkCreateRequest request) {
        return Results.success(knowledgeChunkService.create(docId, request));
    }

    @PutMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> update(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId,
                               @RequestBody KnowledgeChunkUpdateRequest request) {
        knowledgeChunkService.update(docId, chunkId, request);
        return Results.success();
    }

    @DeleteMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> delete(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId) {
        knowledgeChunkService.delete(docId, chunkId);
        return Results.success();
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable")
    public Result<Void> enable(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId,
                               @RequestParam("value") boolean enabled) {
        knowledgeChunkService.enableChunk(docId, chunkId, enabled);
        return Results.success();
    }

    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/batch-enable")
    public Result<Void> batchEnable(@PathVariable("doc-id") String docId,
                                     @RequestParam("value") boolean enabled,
                                     @RequestBody(required = false) KnowledgeChunkBatchRequest request) {
        knowledgeChunkService.batchToggleEnabled(docId, request, enabled);
        return Results.success();
    }
}
