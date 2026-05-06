package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeChunkView(
        Long id,
        Long kbId,
        Long docId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer charCount,
        Integer tokenCount,
        Integer enabled,
        String metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
