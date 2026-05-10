package com.personalblog.ragbackend.common.web.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 发送器包装，统一事件推送和异常处理。
 */
public class SseEmitterSender {
    private final SseEmitter emitter;

    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void sendEvent(String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE event send failed: " + eventName, exception);
        }
    }

    public void complete() {
        emitter.complete();
    }

    public void fail(Throwable error) {
        emitter.completeWithError(error);
    }
}
