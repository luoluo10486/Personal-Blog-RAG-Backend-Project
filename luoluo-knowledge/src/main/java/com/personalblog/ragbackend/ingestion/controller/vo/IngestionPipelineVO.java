package com.personalblog.ragbackend.ingestion.controller.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class IngestionPipelineVO {
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private List<IngestionPipelineNodeVO> nodes;
    private Date createTime;
    private Date updateTime;
}
