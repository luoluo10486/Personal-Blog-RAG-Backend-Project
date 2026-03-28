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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

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

    public RagDemoChatResponse chat(RagDemoChatRequest request) {
        if (!ragProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "rag demo is disabled");
        }
        if (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "siliconflow api key is not configured");
        }

        String systemPrompt = normalizeSystemPrompt(request.systemPrompt());
        String requestJson = buildRequestJson(systemPrompt, request.message().trim());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ragProperties.getApiUrl()))
                .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

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

    private String normalizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return ragProperties.getSystemPrompt().trim();
        }
        return systemPrompt.trim();
    }

    private String buildRequestJson(String systemPrompt, String userMessage) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ragProperties.getModel());
        requestBody.put("temperature", ragProperties.getTemperature());
        requestBody.put("max_tokens", ragProperties.getMaxTokens());
        requestBody.put("stream", false);

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
}
