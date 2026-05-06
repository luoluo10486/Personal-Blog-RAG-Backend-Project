package com.personalblog.ragbackend.knowledge.dto.admin;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeChunkCreateRequest {
    @NotBlank(message = "Chunk 内容不能为空")
    private String content;
    private Integer index;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}
