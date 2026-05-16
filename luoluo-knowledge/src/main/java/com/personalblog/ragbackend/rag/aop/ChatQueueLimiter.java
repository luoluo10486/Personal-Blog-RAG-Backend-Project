package com.personalblog.ragbackend.rag.aop;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Legacy compatibility bean for older AOP wiring.
 * The real queueing implementation now lives in {@link com.personalblog.ragbackend.rag.service.ratelimit.ChatQueueLimiter}.
 */
@Deprecated
@Component("legacyChatQueueLimiter")
@RequiredArgsConstructor
public class ChatQueueLimiter {
    private final com.personalblog.ragbackend.rag.service.ratelimit.ChatQueueLimiter delegate;

    public void enqueue(String question, String conversationId, SseEmitter emitter, Runnable onAcquire) {
        delegate.enqueue(question, conversationId, emitter, onAcquire);
    }
}
