package com.personalblog.ragbackend.dto.rag;

import java.util.List;

/**
 * RagQueryResponse 数据传输对象，用于接口参数与返回值封装。
 */
public record RagQueryResponse(
        String answer,
        List<RagReferenceResponse> references
) {
}

