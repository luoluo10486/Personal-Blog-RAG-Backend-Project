package com.personalblog.ragbackend.infra.ai.chat;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;

import java.util.List;

public interface LLMService {

    default String chat(String prompt) {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return chat(req);
    }

    String chat(ChatRequest request);

    default String chat(ChatRequest request, String modelId) {
        return chat(request);
    }

    default StreamCancellationHandle streamChat(String prompt, StreamCallback callback) {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .build();
        return streamChat(req, callback);
    }

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);
}
