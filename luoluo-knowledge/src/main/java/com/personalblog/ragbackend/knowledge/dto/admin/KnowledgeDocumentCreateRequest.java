package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeDocumentCreateRequest {
    private String kbId;
    private String docName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private Integer enabled;
}
