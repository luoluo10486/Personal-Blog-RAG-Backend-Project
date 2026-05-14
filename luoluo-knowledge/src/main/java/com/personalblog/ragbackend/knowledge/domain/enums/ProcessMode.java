package com.personalblog.ragbackend.knowledge.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ProcessMode {
    CHUNK("chunk"),
    PIPELINE("pipeline");

    private final String value;

    public static ProcessMode fromValue(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return CHUNK;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ProcessMode mode : values()) {
            if (mode.value.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown process mode: " + value);
    }
}
