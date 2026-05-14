package com.personalblog.ragbackend.ingestion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskNodeVO;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskVO;
import com.personalblog.ragbackend.ingestion.domain.result.IngestionResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IngestionTaskService {

    IngestionResult execute(IngestionTaskCreateRequest request);

    IngestionResult upload(String pipelineId, MultipartFile file);

    IngestionTaskVO get(String taskId);

    IPage<IngestionTaskVO> page(Page<IngestionTaskVO> page, String status);

    List<IngestionTaskNodeVO> listNodes(String taskId);
}
