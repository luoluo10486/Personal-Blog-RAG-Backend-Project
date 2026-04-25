package com.personalblog.ragbackend.infra.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import com.personalblog.ragbackend.infra.ai.trace.RagTraceNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;

@Service
public class OllamaChatClient extends AbstractOpenAIStyleChatClient {

    public OllamaChatClient(@Qualifier("aiHttpClient") HttpClient httpClient,
                            ObjectMapper objectMapper,
                            AIModelProperties aiProperties,
                            @Qualifier("aiStreamExecutor") Executor streamExecutor) {
        super(httpClient, objectMapper, aiProperties, streamExecutor);
    }

    @Override
    public String provider() {
        return ModelProvider.OLLAMA.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    @RagTraceNode(name = "ollama-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    @RagTraceNode(name = "ollama-stream-chat", type = "LLM_PROVIDER")
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}
