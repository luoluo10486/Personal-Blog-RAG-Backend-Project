package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeDocumentChunkLogView(
        Long id,
        Long docId,
        String status,
        String processMode,
        String chunkStrategy,
        Long pipelineId,
        Long extractDuration,
        Long chunkDuration,
        Long embedDuration,
        Long persistDuration,
        Long totalDuration,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
