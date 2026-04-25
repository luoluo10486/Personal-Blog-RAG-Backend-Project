package com.personalblog.ragbackend.infra.ai.enums;

public enum ModelCapability {
    CHAT("chat"),
    EMBEDDING("embedding"),
    RERANK("rerank");

    private final String displayName;

    ModelCapability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
