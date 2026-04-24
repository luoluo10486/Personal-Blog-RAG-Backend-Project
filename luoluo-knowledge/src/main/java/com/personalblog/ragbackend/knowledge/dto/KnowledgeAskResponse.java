package com.personalblog.ragbackend.knowledge.dto;

import java.util.List;

public record KnowledgeAskResponse(
        String answer,
        String baseCode,
        List<KnowledgeCitation> citations,
        KnowledgeTrace trace
) {
}
