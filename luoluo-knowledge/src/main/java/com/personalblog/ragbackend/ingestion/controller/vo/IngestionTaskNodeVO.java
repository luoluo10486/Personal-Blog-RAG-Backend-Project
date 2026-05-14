package com.personalblog.ragbackend.ingestion.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionTaskNodeVO {
    private String id;
    private String taskId;
    private String pipelineId;
    private String nodeId;
    private String nodeType;
    private Integer nodeOrder;
    private String status;
    private Long durationMs;
    private String message;
    private String errorMessage;
    private Map<String, Object> output;
    private Date createTime;
    private Date updateTime;
}
