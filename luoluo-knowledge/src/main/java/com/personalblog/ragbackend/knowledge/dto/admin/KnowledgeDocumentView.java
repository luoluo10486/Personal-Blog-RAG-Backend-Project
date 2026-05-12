package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeDocumentView(
        String id,
        String kbId,
        String docName,
        String sourceType,
        String sourceLocation,
        Integer scheduleEnabled,
        String scheduleCron,
        Boolean enabled,
        Integer chunkCount,
        String fileUrl,
        String fileType,
        Long fileSize,
        String chunkStrategy,
        String processMode,
        String chunkConfig,
        String pipelineId,
        String status,
        String createdBy,
        String updatedBy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
