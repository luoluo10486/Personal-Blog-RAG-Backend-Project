package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineView;
import com.personalblog.ragbackend.knowledge.service.ingestion.IngestionPipelineService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@MemberLoginRequired
public class IngestionPipelineController {
    private final IngestionPipelineService ingestionPipelineService;

    public IngestionPipelineController(IngestionPipelineService ingestionPipelineService) {
        this.ingestionPipelineService = ingestionPipelineService;
    }

    @PostMapping("/ingestion/pipelines")
    public R<IngestionPipelineView> create(@RequestBody IngestionPipelineCreateRequest request) {
        return R.ok(ingestionPipelineService.create(request));
    }

    @PutMapping("/ingestion/pipelines/{id}")
    public R<IngestionPipelineView> update(@PathVariable Long id,
                                           @RequestBody IngestionPipelineUpdateRequest request) {
        return R.ok(ingestionPipelineService.update(id, request));
    }

    @GetMapping("/ingestion/pipelines/{id}")
    public R<IngestionPipelineView> get(@PathVariable Long id) {
        return R.ok(ingestionPipelineService.get(id));
    }

    @GetMapping("/ingestion/pipelines")
    public R<IPage<IngestionPipelineView>> page(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                                @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                                @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(ingestionPipelineService.page(pageNo, pageSize, keyword));
    }

    @DeleteMapping("/ingestion/pipelines/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ingestionPipelineService.delete(id);
        return R.ok();
    }
}
