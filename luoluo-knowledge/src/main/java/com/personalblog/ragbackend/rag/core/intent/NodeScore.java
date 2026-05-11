package com.personalblog.ragbackend.rag.core.intent;

public record NodeScore(
        RagIntentNode node,
        double score,
        String reason
) {
}
