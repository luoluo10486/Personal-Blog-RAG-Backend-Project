package com.personalblog.ragbackend.ingestion.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SourceType {
    FILE("file"),
    URL("url"),
    FEISHU("feishu"),
    S3("s3");

    private final String value;

    @JsonCreator
    public static SourceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        for (SourceType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown source type: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase().replace('-', '_');
    }
}
