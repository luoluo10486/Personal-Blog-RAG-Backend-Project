package com.personalblog.ragbackend.infra.ai.enums;

public enum ModelProvider {
    OLLAMA("ollama"),
    BAI_LIAN("bailian"),
    SILICON_FLOW("siliconflow"),
    NOOP("noop");

    private final String id;

    ModelProvider(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean matches(String provider) {
        return provider != null && provider.equalsIgnoreCase(id);
    }
}
