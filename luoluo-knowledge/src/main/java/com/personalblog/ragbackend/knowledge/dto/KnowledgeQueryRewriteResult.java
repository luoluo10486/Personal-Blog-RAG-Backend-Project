package com.personalblog.ragbackend.knowledge.dto;

import java.util.List;

public record KnowledgeQueryRewriteResult(
        String originalQuestion,
        String rewrittenQuestion,
        List<String> appliedMappings,
        List<String> subQuestions
) {
}
