package com.personalblog.ragbackend.ingestion.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IngestionStatus {
    PENDING("pending"),
    RUNNING("running"),
    FAILED("failed"),
    COMPLETED("completed");

    private final String value;

    @JsonCreator
    public static IngestionStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        if ("success".equalsIgnoreCase(normalized)) {
            return COMPLETED;
        }
        for (IngestionStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ingestion status: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase().replace('-', '_');
    }
}
