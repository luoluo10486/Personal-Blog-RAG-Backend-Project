package com.personalblog.ragbackend.dto.rag;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 演示问答请求，包含可选系统提示词和用户输入内容。
 */
public record RagDemoChatRequest(
        String systemPrompt,
        @NotBlank(message = "消息内容不能为空")
        String message
) {
}
