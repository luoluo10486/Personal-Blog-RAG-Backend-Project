package com.personalblog.ragbackend.dto.document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文档分块（chunk）接口响应结构。
 */
public record DocumentChunkResponse(
        boolean success,
        String mimeType,
        Map<String, String> metadata,
        int contentLength,
        int chunkCount,
        int targetChunkSize,
        int maxChunkSize,
        int overlapSize,
        List<DocumentChunk> chunks,
        String errorMessage
) {
    public static DocumentChunkResponse success(
            String mimeType,
            Map<String, String> metadata,
            int contentLength,
            int targetChunkSize,
            int maxChunkSize,
            int overlapSize,
            List<DocumentChunk> chunks
    ) {
        Map<String, String> safeMetadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
        List<DocumentChunk> safeChunks = chunks == null ? List.of() : List.copyOf(chunks);
        return new DocumentChunkResponse(
                true,
                mimeType,
                safeMetadata,
                contentLength,
                safeChunks.size(),
                targetChunkSize,
                maxChunkSize,
                overlapSize,
                safeChunks,
                null
        );
    }

    public static DocumentChunkResponse failure(String errorMessage) {
        return new DocumentChunkResponse(
                false,
                null,
                Collections.emptyMap(),
                0,
                0,
                0,
                0,
                0,
                List.of(),
                errorMessage
        );
    }
}
