package com.personalblog.ragbackend.rag.enums;

public enum IntentKind {
    KB(0),
    SYSTEM(1),
    MCP(2);

    private final int code;

    IntentKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static IntentKind fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentKind value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
