package com.personalblog.ragbackend.rag.enums;

public enum IntentLevel {
    DOMAIN(0),
    CATEGORY(1),
    TOPIC(2);

    private final int code;

    IntentLevel(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static IntentLevel fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentLevel value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
