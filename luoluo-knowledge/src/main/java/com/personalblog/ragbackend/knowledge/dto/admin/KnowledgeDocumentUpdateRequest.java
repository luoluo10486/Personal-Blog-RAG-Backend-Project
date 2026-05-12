package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeDocumentUpdateRequest {
    private String docName;
    private String processMode;
    private String chunkStrategy;
    private String chunkConfig;
    private String pipelineId;
    private String sourceLocation;
    private Integer scheduleEnabled;
    private String scheduleCron;
}
