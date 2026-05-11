package com.personalblog.ragbackend.rag.core.intent;

public record GuidanceDecision(
        boolean prompt,
        String promptText
) {
    public static GuidanceDecision none() {
        return new GuidanceDecision(false, null);
    }

    public static GuidanceDecision prompt(String promptText) {
        return new GuidanceDecision(true, promptText);
    }
}
