package com.personalblog.ragbackend.knowledge.core.chunk;

import java.util.Locale;

public enum ChunkingMode {
    STRUCTURE_AWARE("structure-aware"),
    FIXED_SIZE("fixed-size");

    private final String code;

    ChunkingMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ChunkingMode from(String value) {
        if (value == null || value.isBlank()) {
            return STRUCTURE_AWARE;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');

        for (ChunkingMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }
        return STRUCTURE_AWARE;
    }
}
