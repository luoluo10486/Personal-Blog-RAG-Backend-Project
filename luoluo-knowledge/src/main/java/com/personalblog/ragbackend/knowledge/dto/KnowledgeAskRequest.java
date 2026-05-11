package com.personalblog.ragbackend.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeAskRequest(
        @NotBlank(message = "question 涓嶈兘涓虹┖")
        String question,
        String baseCode,
        Integer topK,
        String conversationId,
        Boolean deepThinking
) {
    public KnowledgeAskRequest(String question, String baseCode, Integer topK) {
        this(question, baseCode, topK, null, false);
    }

    public KnowledgeAskRequest(String question, String baseCode, Integer topK, String conversationId) {
        this(question, baseCode, topK, conversationId, false);
    }
}
