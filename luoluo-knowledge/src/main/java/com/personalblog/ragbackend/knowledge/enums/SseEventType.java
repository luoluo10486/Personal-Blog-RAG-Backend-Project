package com.personalblog.ragbackend.knowledge.enums;

/**
 * SSE 事件类型。
 */
public enum SseEventType {
    META("meta"),
    MESSAGE("message"),
    FINISH("finish"),
    DONE("done"),
    CANCEL("cancel"),
    REJECT("reject");

    private final String value;

    SseEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
