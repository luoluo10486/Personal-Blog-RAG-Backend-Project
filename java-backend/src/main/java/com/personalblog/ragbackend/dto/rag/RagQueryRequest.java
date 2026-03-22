package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RagQueryRequest 数据传输对象，用于接口参数与返回值封装。
 */
public record RagQueryRequest(
        @NotBlank
        @Size(min = 2, max = 500)
        String question
) {
}

