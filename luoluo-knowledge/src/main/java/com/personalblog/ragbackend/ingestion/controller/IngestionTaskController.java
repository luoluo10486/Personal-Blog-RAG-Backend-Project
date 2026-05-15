package com.personalblog.ragbackend.ingestion.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskNodeVO;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskVO;
import com.personalblog.ragbackend.ingestion.domain.result.IngestionResult;
import com.personalblog.ragbackend.ingestion.service.IngestionTaskService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class IngestionTaskController {

    private final IngestionTaskService taskService;

    @PostMapping("/ingestion/tasks")
    public Result<IngestionResult> create(@RequestBody IngestionTaskCreateRequest request) {
        return Results.success(taskService.execute(request));
    }

    @SneakyThrows
    @PostMapping(value = "/ingestion/tasks/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<IngestionResult> upload(@RequestParam(value = "pipelineId") String pipelineId,
                                          @RequestPart("file") MultipartFile file) {
        return Results.success(taskService.upload(pipelineId, file));
    }

    @GetMapping("/ingestion/tasks/{id}")
    public Result<IngestionTaskVO> get(@PathVariable String id) {
        return Results.success(taskService.get(id));
    }

    @GetMapping("/ingestion/tasks/{id}/nodes")
    public Result<List<IngestionTaskNodeVO>> nodes(@PathVariable String id) {
        return Results.success(taskService.listNodes(id));
    }

    @GetMapping("/ingestion/tasks")
    public Result<IPage<IngestionTaskVO>> page(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                               @RequestParam(value = "status", required = false) String status) {
        return Results.success(taskService.page(new Page<>(pageNo, pageSize), status));
    }
}
