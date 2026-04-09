package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * RAG 生成请求：先做检索，再基于召回内容生成带引用的答案。
 */
public record RagGenerationRequest(
        @NotBlank(message = "检索问题不能为空")
        String query,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK,
        String systemPrompt
) {
}
