package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * SiliconFlow 聊天演示服务，负责构造请求、调用模型并解析普通或流式响应。
 */
@Service
public class SiliconFlowChatDemoService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public SiliconFlowChatDemoService(HttpClient ragHttpClient, ObjectMapper objectMapper, RagProperties ragProperties) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    /**
     * 以普通 HTTP 方式调用模型，等待完整答案后统一返回。
     */
    public RagDemoChatResponse chat(RagDemoChatRequest request) {
        validateAvailability();
        HttpRequest httpRequest = buildHttpRequest(request, false);

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "siliconflow request failed: status=" + response.statusCode() + ", body=" + response.body()
                );
            }
            return parseResponse(response.body());
        } catch (HttpConnectTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "siliconflow connect timed out after " + ragProperties.getConnectTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "siliconflow request timed out after " + ragProperties.getReadTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow request interrupted", exception);
        }
    }

    /**
     * 以 SSE 方式输出增量内容，供前端实时消费。
     */
    public SseEmitter streamChat(RagDemoChatRequest request) {
        validateAvailability();

        SseEmitter emitter = new SseEmitter(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds() + 5L).toMillis());
        CompletableFuture.runAsync(() -> streamChatInternal(request, emitter));
        return emitter;
    }

    /**
     * 解析 SiliconFlow 返回的 SSE 数据流，并在读取到增量文本时触发回调。
     */
    RagDemoChatResponse parseStreamingResponse(InputStream responseBody, Consumer<String> deltaConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            StringBuilder fullContent = new StringBuilder();
            String requestId = null;
            String model = ragProperties.getModel();
            String finishReason = null;
            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode chunk = objectMapper.readTree(data);
                requestId = firstNonBlank(asNullableText(chunk.path("id")), requestId);
                model = firstNonBlank(asNullableText(chunk.path("model")), model);

                JsonNode choice = chunk.path("choices").path(0);
                JsonNode delta = choice.path("delta");
                JsonNode contentNode = delta.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String content = contentNode.asText();
                    if (!content.isEmpty()) {
                        deltaConsumer.accept(content);
                        fullContent.append(content);
                    }
                }

                finishReason = firstNonBlank(asNullableText(choice.path("finish_reason")), finishReason);

                JsonNode usage = chunk.path("usage");
                if (!usage.isMissingNode()) {
                    promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                    completionTokens = usage.path("completion_tokens").asInt(completionTokens);
                    totalTokens = usage.path("total_tokens").asInt(totalTokens);
                }
            }

            if (fullContent.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow stream does not contain answer content");
            }

            return new RagDemoChatResponse(
                    requestId,
                    model,
                    fullContent.toString(),
                    finishReason,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );
        }
    }

    /**
     * 真正执行流式模型请求，并将过程事件推送给调用方。
     */
    private void streamChatInternal(RagDemoChatRequest request, SseEmitter emitter) {
        HttpRequest httpRequest = buildHttpRequest(request, true);

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream errorBody = response.body()) {
                    String body = new String(errorBody.readAllBytes(), StandardCharsets.UTF_8);
                    sendErrorEvent(emitter, "siliconflow request failed: status=" + response.statusCode() + ", body=" + body);
                }
                emitter.complete();
                return;
            }

            try (InputStream responseBody = response.body()) {
                RagDemoChatResponse finalResponse = parseStreamingResponse(responseBody, content -> sendDeltaEvent(emitter, content));
                emitter.send(SseEmitter.event().name("complete").data(finalResponse));
                emitter.complete();
            }
        } catch (HttpConnectTimeoutException exception) {
            completeStreamWithError(
                    emitter,
                    "siliconflow connect timed out after " + ragProperties.getConnectTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            completeStreamWithError(
                    emitter,
                    "siliconflow request timed out after " + ragProperties.getReadTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (UncheckedIOException exception) {
            emitter.completeWithError(exception.getCause());
        } catch (IOException exception) {
            completeStreamWithError(emitter, "siliconflow request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            completeStreamWithError(emitter, "siliconflow request interrupted", exception);
        }
    }

    /**
     * 校验 RAG 功能开关和关键配置，避免在配置不完整时继续调用模型。
     */
    private void validateAvailability() {
        if (!ragProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "rag demo is disabled");
        }
        if (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "siliconflow api key is not configured");
        }
    }

    /**
     * 优先使用请求中的系统提示词；为空时回退到默认配置。
     */
    private String normalizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return ragProperties.getSystemPrompt().trim();
        }
        return systemPrompt.trim();
    }

    /**
     * 将业务请求转换为可直接发送的 HTTP 请求对象。
     */
    private HttpRequest buildHttpRequest(RagDemoChatRequest request, boolean stream) {
        String systemPrompt = normalizeSystemPrompt(request.systemPrompt());
        String requestJson = buildRequestJson(systemPrompt, request.message().trim(), stream);

        return HttpRequest.newBuilder()
                .uri(URI.create(ragProperties.getApiUrl()))
                .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
    }

    /**
     * 序列化生成 SiliconFlow 所需的 JSON 请求体。
     */
    private String buildRequestJson(String systemPrompt, String userMessage, boolean stream) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ragProperties.getModel());
        requestBody.put("temperature", ragProperties.getTemperature());
        requestBody.put("max_tokens", ragProperties.getMaxTokens());
        requestBody.put("stream", stream);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessageNode = messages.addObject();
        userMessageNode.put("role", "user");
        userMessageNode.put("content", userMessage);

        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to serialize siliconflow request", exception);
        }
    }

    /**
     * 解析非流式模型响应，提取答案和 token 统计信息。
     */
    private RagDemoChatResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0);
        String answer = choice.path("message").path("content").asText(null);
        if (answer == null || answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow response does not contain answer content");
        }

        JsonNode usage = root.path("usage");
        return new RagDemoChatResponse(
                root.path("id").asText(null),
                root.path("model").asText(ragProperties.getModel()),
                answer,
                choice.path("finish_reason").asText(null),
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0)
        );
    }

    /**
     * 发送模型增量文本事件。
     */
    private void sendDeltaEvent(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(content));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 发送统一错误事件，方便前端按固定结构处理异常。
     */
    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 在流式过程中发送错误信息并结束 SSE 连接。
     */
    private void completeStreamWithError(SseEmitter emitter, String message, Exception exception) {
        try {
            sendErrorEvent(emitter, message);
            emitter.complete();
        } catch (UncheckedIOException sendException) {
            emitter.completeWithError(exception);
        }
    }

    /**
     * 将 JsonNode 转成可空文本，空节点或空白字符串统一视为 null。
     */
    private String asNullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * 返回优先值；若优先值为空则回退到兜底值。
     */
    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }
}
