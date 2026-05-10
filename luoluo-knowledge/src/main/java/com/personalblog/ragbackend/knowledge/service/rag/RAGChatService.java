package com.personalblog.ragbackend.knowledge.service.rag;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RAGChatService {

    void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter);

    void stopTask(String taskId);
}
