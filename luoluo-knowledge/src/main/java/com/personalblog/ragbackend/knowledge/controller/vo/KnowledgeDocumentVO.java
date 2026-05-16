package com.personalblog.ragbackend.knowledge.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Knowledge document view object.
 */
@Data
public class KnowledgeDocumentVO {
    private String id;
    private String kbId;
    private String docName;
    private String sourceType;
    private String sourceLocation;
    private Integer scheduleEnabled;
    private String scheduleCron;
    private Boolean enabled;
    private Integer chunkCount;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private String chunkStrategy;
    private String processMode;
    private String chunkConfig;
    private String pipelineId;
    private String status;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
