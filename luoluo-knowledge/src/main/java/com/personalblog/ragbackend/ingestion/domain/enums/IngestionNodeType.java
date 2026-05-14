package com.personalblog.ragbackend.ingestion.domain.enums;

public enum IngestionNodeType {
    FETCHER("fetcher"),
    PARSER("parser"),
    ENHANCER("enhancer"),
    CHUNKER("chunker"),
    ENRICHER("enricher"),
    INDEXER("indexer");

    private final String value;

    IngestionNodeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static IngestionNodeType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase().replace('-', '_');
        for (IngestionNodeType type : values()) {
            if (type.value.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + value);
    }
}
