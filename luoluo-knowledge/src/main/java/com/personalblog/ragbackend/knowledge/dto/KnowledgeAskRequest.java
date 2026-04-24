package com.personalblog.ragbackend.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeAskRequest(
        @NotBlank(message = "question 不能为空")
        String question,
        String baseCode,
        Integer topK
) {
}
