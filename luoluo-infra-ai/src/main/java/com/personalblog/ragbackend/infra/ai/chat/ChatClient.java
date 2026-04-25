package com.personalblog.ragbackend.infra.ai.chat;

import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

public interface ChatClient {

    String provider();

    String chat(ChatRequest request, ModelTarget target);

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target);
}
