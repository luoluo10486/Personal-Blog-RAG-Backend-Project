package com.personalblog.ragbackend.infra.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.http.HttpMediaTypes;
import com.personalblog.ragbackend.infra.ai.http.HttpResponseHelper;
import com.personalblog.ragbackend.infra.ai.http.ModelClientErrorType;
import com.personalblog.ragbackend.infra.ai.http.ModelClientException;
import com.personalblog.ragbackend.infra.ai.http.ModelUrlResolver;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractOpenAIStyleEmbeddingClient implements EmbeddingClient {

    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;
    protected final AIModelProperties aiProperties;

    protected AbstractOpenAIStyleEmbeddingClient(HttpClient httpClient,
                                                 ObjectMapper objectMapper,
                                                 AIModelProperties aiProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    protected boolean requiresApiKey() {
        return true;
    }

    protected void customizeRequestBody(ObjectNode body, ModelTarget target) {
    }

    protected int maxBatchSize() {
        return 0;
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

        int batchSize = maxBatchSize();
        if (batchSize <= 0 || texts.size() <= batchSize) {
            return doEmbed(texts, target);
        }

        List<List<Float>> results = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index += batchSize) {
            int end = Math.min(index + batchSize, texts.size());
            results.addAll(doEmbed(texts.subList(index, end), target));
        }
        return results;
    }

    protected List<List<Float>> doEmbed(List<String> texts, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
        if (requiresApiKey()) {
            HttpResponseHelper.requireApiKey(provider, provider());
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.EMBEDDING)))
                .timeout(Duration.ofSeconds(aiProperties.getReadTimeoutSeconds()))
                .header("Content-Type", HttpMediaTypes.APPLICATION_JSON);
        if (requiresApiKey()) {
            requestBuilder.header("Authorization", "Bearer " + provider.getApiKey());
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(writeBody(buildRequestBody(texts, target))))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelClientException(
                        provider() + " embedding request failed: HTTP " + response.statusCode(),
                        ModelClientErrorType.fromHttpStatus(response.statusCode()),
                        response.statusCode()
                );
            }
            return parseEmbeddings(HttpResponseHelper.parseJson(response.body(), provider()), texts.size());
        } catch (ModelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelClientException(
                    provider() + " embedding request failed: " + ex.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR,
                    null,
                    ex
            );
        }
    }

    protected ObjectNode buildRequestBody(List<String> texts, ModelTarget target) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", HttpResponseHelper.requireModel(target, provider()));
        Integer dimension = target.candidate().getDimension();
        if (dimension != null && dimension > 0) {
            body.put("dimensions", dimension);
        }
        ArrayNode input = body.putArray("input");
        for (String text : texts) {
            input.add(text);
        }
        customizeRequestBody(body, target);
        return body;
    }

    protected List<List<Float>> parseEmbeddings(JsonNode response, int expectedSize) {
        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty() || data.size() != expectedSize) {
            throw new ModelClientException(
                    provider() + " embeddings response is invalid",
                    ModelClientErrorType.INVALID_RESPONSE,
                    null
            );
        }

        List<List<Float>> results = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new ModelClientException(
                        provider() + " embeddings response is missing embedding",
                        ModelClientErrorType.INVALID_RESPONSE,
                        null
                );
            }
            results.add(toVector(embedding));
        }
        return results;
    }

    protected List<Float> toVector(JsonNode embedding) {
        List<Float> vector = new ArrayList<>(embedding.size());
        for (JsonNode value : embedding) {
            vector.add((float) value.asDouble());
        }
        return vector;
    }

    private String writeBody(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize embedding request", ex);
        }
    }
}
