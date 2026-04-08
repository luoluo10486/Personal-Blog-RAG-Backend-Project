package com.personalblog.ragbackend.dto.document;

/**
 * 文档分块（chunk）结果中的单条 chunk。
 */
public record DocumentChunk(
        int chunkIndex,
        String sectionTitle,
        String content,
        int contentLength,
        boolean overlapFromPrevious
) {
}
