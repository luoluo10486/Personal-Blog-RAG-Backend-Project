package com.personalblog.ragbackend.knowledge.dto.admin;

import java.util.Date;

public record KnowledgeBaseView(
        String id,
        String name,
        String embeddingModel,
        String collectionName,
        Long documentCount,
        String createdBy,
        Date createTime,
        Date updateTime
) {
}
