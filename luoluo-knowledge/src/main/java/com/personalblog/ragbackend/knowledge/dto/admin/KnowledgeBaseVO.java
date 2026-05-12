package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

import java.util.Date;

@Data
public class KnowledgeBaseVO {
    private String id;
    private String name;
    private String embeddingModel;
    private String collectionName;
    private Long documentCount;
    private String createdBy;
    private Date createTime;
    private Date updateTime;
}
