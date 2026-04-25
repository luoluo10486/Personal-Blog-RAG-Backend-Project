package com.personalblog.ragbackend.infra.ai.chat;

public interface StreamCallback {

    void onContent(String content);

    default void onThinking(String content) {
    }

    void onComplete();

    void onError(Throwable error);
}
