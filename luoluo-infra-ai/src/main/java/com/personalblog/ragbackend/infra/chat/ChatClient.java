package com.personalblog.ragbackend.infra.chat;

import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.model.ModelTarget;

public interface ChatClient {

    String provider();

    String chat(ChatRequest request, ModelTarget target);

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target);
}
