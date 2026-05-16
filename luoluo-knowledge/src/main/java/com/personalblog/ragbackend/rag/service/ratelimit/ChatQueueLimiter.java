package com.personalblog.ragbackend.rag.service.ratelimit;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ChatQueueLimiter {
    private final FairDistributedRateLimiter fairDistributedRateLimiter;

    public ChatQueueLimiter(FairDistributedRateLimiter fairDistributedRateLimiter) {
        this.fairDistributedRateLimiter = fairDistributedRateLimiter;
    }

    public void enqueue(String question, String conversationId, SseEmitter emitter, Runnable onAcquire) {
        fairDistributedRateLimiter.enqueue(question, conversationId, emitter, onAcquire);
    }
}
