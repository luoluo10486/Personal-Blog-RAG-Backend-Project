package com.personalblog.ragbackend.knowledge.controller.vo;

import lombok.Data;

import java.util.Date;

@Data
public class KnowledgeDocumentChunkLogVO {
    private String id;
    private String docId;
    private String status;
    private String processMode;
    private String chunkStrategy;
    private String pipelineId;
    private String pipelineName;
    private Long extractDuration;
    private Long chunkDuration;
    private Long embedDuration;
    private Long persistDuration;
    private Long otherDuration;
    private Long totalDuration;
    private Integer chunkCount;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
    private Date createTime;
}
