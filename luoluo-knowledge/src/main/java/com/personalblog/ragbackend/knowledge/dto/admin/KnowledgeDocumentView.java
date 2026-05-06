package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeDocumentView(
        Long id,
        Long kbId,
        String docName,
        String sourceType,
        String sourceLocation,
        Integer scheduleEnabled,
        String scheduleCron,
        Integer enabled,
        Integer chunkCount,
        String fileUrl,
        String fileType,
        Long fileSize,
        String chunkStrategy,
        String processMode,
        String chunkConfig,
        Long pipelineId,
        String status,
        String contentHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
