package com.personalblog.ragbackend.infra.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import com.personalblog.ragbackend.infra.ai.http.HttpResponseHelper;
import com.personalblog.ragbackend.infra.ai.http.ModelClientErrorType;
import com.personalblog.ragbackend.infra.ai.http.ModelClientException;
import com.personalblog.ragbackend.infra.ai.http.ModelUrlResolver;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SiliconFlowEmbeddingClient implements EmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AIModelProperties aiProperties;

    public SiliconFlowEmbeddingClient(@Qualifier("aiHttpClient") HttpClient httpClient,
                                      ObjectMapper objectMapper,
                                      AIModelProperties aiProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    public List<Float> embed(String text, ModelTarget target) {
        return embedBatch(List.of(text), target).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", HttpResponseHelper.requireModel(target, provider()));
        body.put("encoding_format", "float");
        if (target.candidate().getDimension() != null) {
            body.put("dimensions", target.candidate().getDimension());
        }
        ArrayNode input = body.putArray("input");
        for (String text : texts) {
            input.add(text);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ModelUrlResolver.resolveUrl(target.provider(), target.candidate(), ModelCapability.EMBEDDING)))
                .timeout(Duration.ofSeconds(aiProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + target.provider().getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeBody(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelClientException(
                        provider() + " embedding 请求失败: HTTP " + response.statusCode(),
                        ModelClientErrorType.fromHttpStatus(response.statusCode()),
                        response.statusCode()
                );
            }
            return parseEmbeddings(response.body());
        } catch (ModelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelClientException(provider() + " embedding 请求失败: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        }
    }

    private List<List<Float>> parseEmbeddings(String body) {
        JsonNode data = HttpResponseHelper.parseJson(body, provider()).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ModelClientException(provider() + " embeddings 响应无效", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        List<List<Float>> results = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new ModelClientException(provider() + " embeddings 响应缺少 embedding", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                vector.add((float) value.asDouble());
            }
            results.add(vector);
        }
        return results;
    }

    private String writeBody(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize embedding request", ex);
        }
    }
}
