package com.personalblog.ragbackend.knowledge.dto.document;

public record DocumentChunk(
        int chunkIndex,
        String sectionTitle,
        String content,
        int contentLength,
        boolean overlapFromPrevious
) {
}
