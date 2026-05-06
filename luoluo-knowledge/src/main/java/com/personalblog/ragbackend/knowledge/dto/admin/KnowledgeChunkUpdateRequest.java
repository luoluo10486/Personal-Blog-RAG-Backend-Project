package com.personalblog.ragbackend.knowledge.dto.admin;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeChunkUpdateRequest {
    @NotBlank(message = "Chunk 内容不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
