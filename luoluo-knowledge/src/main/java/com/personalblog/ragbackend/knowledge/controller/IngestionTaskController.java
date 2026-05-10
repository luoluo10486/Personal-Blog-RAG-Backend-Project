package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskNodeView;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskResult;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskView;
import com.personalblog.ragbackend.knowledge.service.ingestion.IngestionTaskService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping
@MemberLoginRequired
public class IngestionTaskController {
    private final IngestionTaskService ingestionTaskService;

    public IngestionTaskController(IngestionTaskService ingestionTaskService) {
        this.ingestionTaskService = ingestionTaskService;
    }

    @PostMapping(value = {"/ingestion/tasks", "/ingestion/tasks/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<IngestionTaskResult> upload(@RequestParam("baseCode") String baseCode,
                                         @RequestParam(value = "pipelineId", required = false) Long pipelineId,
                                         @RequestParam(value = "sourceType", required = false, defaultValue = "file") String sourceType,
                                         @RequestParam(value = "sourceLocation", required = false) String sourceLocation,
                                         @RequestParam(value = "sourceFileName", required = false) String sourceFileName,
                                         @RequestPart("file") MultipartFile file) {
        IngestionTaskCreateRequest request = new IngestionTaskCreateRequest(
                baseCode,
                pipelineId,
                sourceType,
                sourceLocation,
                sourceFileName == null && file != null ? file.getOriginalFilename() : sourceFileName
        );
        return R.ok(ingestionTaskService.execute(request, file));
    }

    @GetMapping("/ingestion/tasks/{id}")
    public R<IngestionTaskView> get(@PathVariable Long id) {
        return R.ok(ingestionTaskService.get(id));
    }

    @GetMapping("/ingestion/tasks/{id}/nodes")
    public R<List<IngestionTaskNodeView>> nodes(@PathVariable Long id) {
        return R.ok(ingestionTaskService.listNodes(id));
    }

    @GetMapping("/ingestion/tasks")
    public R<IPage<IngestionTaskView>> page(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                            @RequestParam(value = "status", required = false) String status) {
        return R.ok(ingestionTaskService.page(pageNo, pageSize, status));
    }
}
