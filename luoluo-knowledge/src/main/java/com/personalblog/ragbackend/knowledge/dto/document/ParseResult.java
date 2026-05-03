package com.personalblog.ragbackend.knowledge.dto.document;

import java.util.Collections;
import java.util.Map;

public record ParseResult(
        boolean success,
        String mimeType,
        String content,
        Map<String, String> metadata,
        int contentLength,
        String errorMessage
) {
    public static ParseResult success(String mimeType, String content, Map<String, String> metadata) {
        Map<String, String> safeMetadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
        String safeContent = content == null ? "" : content;
        return new ParseResult(true, mimeType, safeContent, safeMetadata, safeContent.length(), null);
    }

    public static ParseResult failure(String errorMessage) {
        return new ParseResult(false, null, null, Collections.emptyMap(), 0, errorMessage);
    }
}
