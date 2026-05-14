package com.personalblog.ragbackend.ingestion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionPipelineVO;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;

public interface IngestionPipelineService {

    IngestionPipelineVO create(IngestionPipelineCreateRequest request);

    IngestionPipelineVO update(String pipelineId, IngestionPipelineUpdateRequest request);

    IngestionPipelineVO get(String pipelineId);

    IPage<IngestionPipelineVO> page(Page<IngestionPipelineVO> page, String keyword);

    void delete(String pipelineId);

    PipelineDefinition getDefinition(String pipelineId);
}
