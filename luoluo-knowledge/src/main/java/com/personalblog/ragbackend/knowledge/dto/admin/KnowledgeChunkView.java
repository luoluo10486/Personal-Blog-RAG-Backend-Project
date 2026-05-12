package com.personalblog.ragbackend.knowledge.dto.admin;

import java.time.LocalDateTime;

public record KnowledgeChunkView(
        String id,
        String kbId,
        String docId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer charCount,
        Integer tokenCount,
        Integer enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
