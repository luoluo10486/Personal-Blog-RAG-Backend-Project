package com.personalblog.ragbackend.core.chunk;

import java.util.Locale;
import java.util.Map;

public enum ChunkingMode {
    FIXED_SIZE("fixed_size", "固定大小", true),
    STRUCTURE_AWARE("structure_aware", "结构感知", true);

    private final String value;
    private final String label;
    private final boolean visible;

    ChunkingMode(String value, String label, boolean visible) {
        this.value = value;
        this.label = label;
        this.visible = visible;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public boolean isVisible() {
        return visible;
    }

    public Map<String, Integer> getDefaultConfig() {
        return switch (this) {
            case FIXED_SIZE -> Map.of("chunkSize", 512, "overlapSize", 128);
            case STRUCTURE_AWARE -> Map.of("targetChars", 1400, "overlapChars", 0, "maxChars", 1800, "minChars", 600);
        };
    }

    public static ChunkingMode from(String value) {
        if (value == null || value.isBlank()) {
            return STRUCTURE_AWARE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ChunkingMode mode : values()) {
            if (mode.value.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return STRUCTURE_AWARE;
    }
}
