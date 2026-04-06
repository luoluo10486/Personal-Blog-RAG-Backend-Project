package com.personalblog.ragbackend.dto.document;

/**
 * A single chunk generated from a parsed document.
 */
public record DocumentChunk(
        int chunkIndex,
        String sectionTitle,
        String content,
        int contentLength,
        boolean overlapFromPrevious
) {
}
