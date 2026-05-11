package com.personalblog.ragbackend.knowledge.service.rag.intent;

public record NodeScore(
        RagIntentNode node,
        double score,
        String reason
) {
}
