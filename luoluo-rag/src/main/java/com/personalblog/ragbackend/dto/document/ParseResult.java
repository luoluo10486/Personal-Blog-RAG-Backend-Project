package com.personalblog.ragbackend.dto.document;

import java.util.Collections;
import java.util.Map;

/**
 * 文档解析结果对象，统一返回解析状态、正文内容和元数据。
 */
public record ParseResult(
        boolean success,
        String mimeType,
        String content,
        Map<String, String> metadata,
        int contentLength,
        String errorMessage
) {

    /**
     * 构造解析成功结果，并自动补齐内容长度。
     */
    public static ParseResult success(String mimeType, String content, Map<String, String> metadata) {
        Map<String, String> safeMetadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
        String safeContent = content == null ? "" : content;
        return new ParseResult(true, mimeType, safeContent, safeMetadata, safeContent.length(), null);
    }

    /**
     * 构造解析失败结果。
     */
    public static ParseResult failure(String errorMessage) {
        return new ParseResult(false, null, null, Collections.emptyMap(), 0, errorMessage);
    }
}
