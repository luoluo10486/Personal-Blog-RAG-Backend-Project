package com.personalblog.ragbackend.infra.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BaiLianRerankClient implements RerankClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AIModelProperties aiProperties;

    public BaiLianRerankClient(@Qualifier("aiHttpClient") HttpClient httpClient,
                               ObjectMapper objectMapper,
                               AIModelProperties aiProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> dedup = new ArrayList<>(candidates.size());
        Set<String> seen = new HashSet<>();
        for (RetrievedChunk candidate : candidates) {
            if (candidate != null && seen.add(candidate.getId())) {
                dedup.add(candidate);
            }
        }
        if (topN <= 0 || dedup.size() <= topN) {
            return dedup;
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", HttpResponseHelper.requireModel(target, provider()));
        ObjectNode input = body.putObject("input");
        input.put("query", query);
        ArrayNode documents = input.putArray("documents");
        for (RetrievedChunk candidate : dedup) {
            documents.add(candidate.getText() == null ? "" : candidate.getText());
        }
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("top_n", topN);
        parameters.put("return_documents", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ModelUrlResolver.resolveUrl(target.provider(), target.candidate(), ModelCapability.RERANK)))
                .timeout(Duration.ofSeconds(aiProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + target.provider().getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeBody(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelClientException(
                        provider() + " rerank 请求失败: HTTP " + response.statusCode(),
                        ModelClientErrorType.fromHttpStatus(response.statusCode()),
                        response.statusCode()
                );
            }
            return parseResults(HttpResponseHelper.parseJson(response.body(), provider()).path("output").path("results"), dedup, topN);
        } catch (ModelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelClientException(provider() + " rerank 请求失败: " + ex.getMessage(), ModelClientErrorType.NETWORK_ERROR, null, ex);
        }
    }

    private List<RetrievedChunk> parseResults(JsonNode results, List<RetrievedChunk> candidates, int topN) {
        if (!results.isArray() || results.isEmpty()) {
            throw new ModelClientException(provider() + " rerank 响应无效", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        List<RetrievedChunk> reranked = new ArrayList<>();
        Set<String> addedIds = new HashSet<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            RetrievedChunk source = candidates.get(index);
            Float score = item.path("relevance_score").isMissingNode() ? null : (float) item.path("relevance_score").asDouble();
            reranked.add(score == null ? source : new RetrievedChunk(source.getId(), source.getText(), score));
            addedIds.add(source.getId());
            if (reranked.size() >= topN) {
                break;
            }
        }
        if (reranked.size() < topN) {
            for (RetrievedChunk candidate : candidates) {
                if (addedIds.add(candidate.getId())) {
                    reranked.add(candidate);
                }
                if (reranked.size() >= topN) {
                    break;
                }
            }
        }
        return reranked;
    }

    private String writeBody(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize rerank request", ex);
        }
    }
}
