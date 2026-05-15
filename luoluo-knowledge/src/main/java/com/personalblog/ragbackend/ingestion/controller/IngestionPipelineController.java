package com.personalblog.ragbackend.ingestion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionPipelineVO;
import com.personalblog.ragbackend.ingestion.service.IngestionPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class IngestionPipelineController {

    private final IngestionPipelineService pipelineService;

    @PostMapping("/ingestion/pipelines")
    public Result<IngestionPipelineVO> create(@RequestBody IngestionPipelineCreateRequest requestParam) {
        return Results.success(pipelineService.create(requestParam));
    }

    @PutMapping("/ingestion/pipelines/{id}")
    public Result<IngestionPipelineVO> update(@PathVariable String id,
                                              @RequestBody IngestionPipelineUpdateRequest requestParam) {
        return Results.success(pipelineService.update(id, requestParam));
    }

    @GetMapping("/ingestion/pipelines/{id}")
    public Result<IngestionPipelineVO> get(@PathVariable String id) {
        return Results.success(pipelineService.get(id));
    }

    @GetMapping("/ingestion/pipelines")
    public Result<IPage<IngestionPipelineVO>> page(Page<IngestionPipelineVO> page,
                                                   @RequestParam(value = "keyword", required = false) String keyword) {
        return Results.success(pipelineService.page(page, keyword));
    }

    @DeleteMapping("/ingestion/pipelines/{id}")
    public Result<Void> delete(@PathVariable String id) {
        pipelineService.delete(id);
        return Results.success();
    }
}
