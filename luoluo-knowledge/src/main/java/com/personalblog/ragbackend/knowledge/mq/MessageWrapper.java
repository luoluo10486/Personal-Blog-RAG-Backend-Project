package com.personalblog.ragbackend.knowledge.mq;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class MessageWrapper<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String keys;
    private T body;
    private String uuid = UUID.randomUUID().toString();
    private Long timestamp = System.currentTimeMillis();

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
