package com.personalblog.ragbackend.dto.rag;

/**
 * RagReferenceResponse 数据传输对象，用于接口参数与返回值封装。
 */
public record RagReferenceResponse(
        String id,
        String title,
        double score
) {
}

