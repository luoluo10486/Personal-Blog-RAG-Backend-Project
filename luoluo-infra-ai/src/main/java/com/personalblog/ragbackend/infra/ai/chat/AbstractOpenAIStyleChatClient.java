package com.personalblog.ragbackend.infra.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.http.HttpMediaTypes;
import com.personalblog.ragbackend.infra.ai.http.HttpResponseHelper;
import com.personalblog.ragbackend.infra.ai.http.ModelClientErrorType;
import com.personalblog.ragbackend.infra.ai.http.ModelClientException;
import com.personalblog.ragbackend.infra.ai.http.ModelUrlResolver;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractOpenAIStyleChatClient implements ChatClient {

    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;
    protected final AIModelProperties aiProperties;
    protected final Executor streamExecutor;

    protected AbstractOpenAIStyleChatClient(HttpClient httpClient,
                                            ObjectMapper objectMapper,
                                            AIModelProperties aiProperties,
                                            Executor streamExecutor) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.streamExecutor = streamExecutor;
    }

    protected boolean isReasoningEnabledForStream(ChatRequest request) {
        return Boolean.TRUE.equals(request.getThinking());
    }

    protected void customizeRequestBody(ObjectNode body, ChatRequest request) {
        if (Boolean.TRUE.equals(request.getThinking())) {
            body.put("enable_thinking", true);
        }
    }

    protected boolean requiresApiKey() {
        return true;
    }

    protected String doChat(ChatRequest request, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
        if (requiresApiKey()) {
            HttpResponseHelper.requireApiKey(provider, provider());
        }

        HttpRequest httpRequest = newAuthorizedRequest(provider, target)
                .header("Content-Type", HttpMediaTypes.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(serializeRequestBody(buildRequestBody(request, target, false))))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelClientException(
                        provider() + " 同步请求失败: HTTP " + response.statusCode() + " - " + response.body(),
                        ModelClientErrorType.fromHttpStatus(response.statusCode()),
                        response.statusCode()
                );
            }
            return extractChatContent(HttpResponseHelper.parseJson(response.body(), provider()));
        } catch (HttpConnectTimeoutException ex) {
            throw new ModelClientException(provider() + " connect timeout: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        } catch (HttpTimeoutException ex) {
            throw new ModelClientException(provider() + " 请求超时: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        } catch (ModelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelClientException(provider() + " 同步请求失败: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        }
    }

    protected StreamCancellationHandle doStreamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
        if (requiresApiKey()) {
            HttpResponseHelper.requireApiKey(provider, provider());
        }

        HttpRequest streamRequest = newAuthorizedRequest(provider, target)
                .header("Content-Type", HttpMediaTypes.APPLICATION_JSON)
                .header("Accept", HttpMediaTypes.TEXT_EVENT_STREAM)
                .POST(HttpRequest.BodyPublishers.ofString(serializeRequestBody(buildRequestBody(request, target, true))))
                .build();

        boolean reasoningEnabled = isReasoningEnabledForStream(request);
        return StreamAsyncExecutor.submit(
                streamExecutor,
                callback,
                cancelled -> doStream(streamRequest, callback, cancelled, reasoningEnabled)
        );
    }

    private void doStream(HttpRequest request,
                          StreamCallback callback,
                          AtomicBoolean cancelled,
                          boolean reasoningEnabled) {
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body;
                try (InputStream responseBody = response.body()) {
                    body = HttpResponseHelper.readBody(responseBody);
                }
                throw new ModelClientException(
                        provider() + " 流式请求失败: HTTP " + response.statusCode() + " - " + body,
                        ModelClientErrorType.fromHttpStatus(response.statusCode()),
                        response.statusCode()
                );
            }

            try (InputStream body = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                boolean completed = false;
                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(line, objectMapper, reasoningEnabled);
                    if (event.hasReasoning()) {
                        callback.onThinking(event.reasoning());
                    }
                    if (event.hasContent()) {
                        callback.onContent(event.content());
                    }
                    if (event.completed()) {
                        callback.onComplete();
                        completed = true;
                        break;
                    }
                }
                if (!cancelled.get() && !completed) {
                    throw new ModelClientException(provider() + " 流式响应异常结束", ModelClientErrorType.INVALID_RESPONSE, null);
                }
            }
        } catch (Exception ex) {
            if (!cancelled.get()) {
                callback.onError(ex);
            }
        }
    }

    protected ObjectNode buildRequestBody(ChatRequest request, ModelTarget target, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", HttpResponseHelper.requireModel(target, provider()));
        if (stream) {
            body.put("stream", true);
        }
        body.set("messages", buildMessages(request.getMessages()));
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            body.put("top_k", request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        customizeRequestBody(body, request);
        return body;
    }

    private ArrayNode buildMessages(List<ChatMessage> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        if (messages == null) {
            return array;
        }
        for (ChatMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            ObjectNode item = array.addObject();
            item.put("role", toOpenAiRole(message.getRole()));
            item.put("content", message.getContent());
        }
        return array;
    }

    private String toOpenAiRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private HttpRequest.Builder newAuthorizedRequest(AIModelProperties.ProviderConfig provider, ModelTarget target) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.CHAT)))
                .timeout(Duration.ofSeconds(aiProperties.getReadTimeoutSeconds()));
        if (requiresApiKey()) {
            builder.header("Authorization", "Bearer " + provider.getApiKey());
        }
        return builder;
    }

    private String extractChatContent(JsonNode response) {
        JsonNode message = response.path("choices").path(0).path("message");
        JsonNode content = message.path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new ModelClientException(provider() + " 响应缺少 content", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return content.asText();
    }

    private String serializeRequestBody(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize chat request", ex);
        }
    }
}
