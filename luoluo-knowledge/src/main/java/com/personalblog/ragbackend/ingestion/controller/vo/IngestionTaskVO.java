package com.personalblog.ragbackend.ingestion.controller.vo;

import com.personalblog.ragbackend.ingestion.domain.context.NodeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionTaskVO {
    private String id;
    private String pipelineId;
    private String sourceType;
    private String sourceLocation;
    private String sourceFileName;
    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private List<NodeLog> logs;
    private Map<String, Object> metadata;
    private Date startedAt;
    private Date completedAt;
    private String createdBy;
    private Date createTime;
    private Date updateTime;
}
