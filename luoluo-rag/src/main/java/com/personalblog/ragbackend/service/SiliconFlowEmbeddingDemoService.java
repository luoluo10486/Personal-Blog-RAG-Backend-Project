package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow Embedding 检索演示服务。
 */
@Service
public class SiliconFlowEmbeddingDemoService {
    private static final int DEFAULT_TOP_K = 3;

    private static final List<DemoChunk> DEMO_CHUNKS = List.of(
            new DemoChunk(
                    "Within 7 days after receipt, unused goods that still allow resale can be returned without reason.",
                    Map.of("doc_id", "policy_001", "title", "Return Policy")
            ),
            new DemoChunk(
                    "Return shipping is paid by the customer unless the product has a quality issue.",
                    Map.of("doc_id", "policy_001", "title", "Return Policy")
            ),
            new DemoChunk(
                    "After shipment, logistics information is updated within 24 hours and can be viewed on the order detail page.",
                    Map.of("doc_id", "logistics_001", "title", "Logistics Guide")
            ),
            new DemoChunk(
                    "Member points can offset cash at checkout. Every 100 points equal 1 unit of currency, capped at 50 percent per order.",
                    Map.of("doc_id", "member_001", "title", "Membership Benefits")
            ),
            new DemoChunk(
                    "Fresh products do not support 7-day no-reason returns. Quality issues must be reported within 48 hours after receipt.",
                    Map.of("doc_id", "policy_002", "title", "Fresh Return Policy")
            )
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public SiliconFlowEmbeddingDemoService(HttpClient ragHttpClient, ObjectMapper objectMapper, RagProperties ragProperties) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    public RagEmbeddingSearchResponse search(RagEmbeddingSearchRequest request) {
        validateAvailability();

        List<String> chunkTexts = DEMO_CHUNKS.stream()
                .map(DemoChunk::content)
                .toList();
        List<double[]> chunkVectors = embed(chunkTexts);
        double[] queryVector = embed(request.query().trim());
        if (chunkVectors.size() != DEMO_CHUNKS.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "siliconflow embedding response size does not match demo chunks"
            );
        }

        int vectorDimension = queryVector.length;
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        List<RagEmbeddingSearchResult> results = buildResults(queryVector, chunkVectors, topK);

        return new RagEmbeddingSearchResponse(
                request.query().trim(),
                ragProperties.getEmbeddingModel(),
                DEMO_CHUNKS.size(),
                vectorDimension,
                results
        );
    }

    List<double[]> embed(List<String> texts) {
        HttpRequest request = buildEmbeddingHttpRequest(texts);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "siliconflow embedding request failed: status=" + response.statusCode() + ", body=" + response.body()
                );
            }
            return parseEmbeddingResponse(response.body());
        } catch (HttpConnectTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "siliconflow embedding connect timed out after " + ragProperties.getConnectTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "siliconflow embedding request timed out after " + ragProperties.getReadTimeoutSeconds() + " seconds",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "siliconflow embedding request failed: " + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow embedding request interrupted", exception);
        }
    }

    double[] embed(String text) {
        List<double[]> embeddings = embed(List.of(text));
        if (embeddings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow embedding response is empty");
        }
        return embeddings.get(0);
    }

    List<double[]> parseEmbeddingResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray() || dataArray.size() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow embedding response does not contain data");
        }

        List<double[]> embeddings = new ArrayList<>();
        for (JsonNode item : dataArray) {
            JsonNode embeddingNode = item.path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.size() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "siliconflow embedding item does not contain embedding");
            }

            double[] vector = new double[embeddingNode.size()];
            for (int index = 0; index < embeddingNode.size(); index++) {
                vector[index] = embeddingNode.path(index).asDouble();
            }
            embeddings.add(vector);
        }
        return embeddings;
    }

    private HttpRequest buildEmbeddingHttpRequest(List<String> texts) {
        return HttpRequest.newBuilder()
                .uri(URI.create(ragProperties.getEmbeddingApiUrl()))
                .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildEmbeddingRequestJson(texts)))
                .build();
    }

    private String buildEmbeddingRequestJson(List<String> texts) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ragProperties.getEmbeddingModel());
        requestBody.put("encoding_format", "float");
        ArrayNode inputNode = requestBody.putArray("input");
        for (String text : texts) {
            inputNode.add(text);
        }

        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to serialize siliconflow embedding request", exception);
        }
    }

    private List<RagEmbeddingSearchResult> buildResults(double[] queryVector, List<double[]> chunkVectors, int topK) {
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (int index = 0; index < DEMO_CHUNKS.size(); index++) {
            DemoChunk chunk = DEMO_CHUNKS.get(index);
            double similarity = CosineSimilarity.calculate(queryVector, chunkVectors.get(index));
            scoredChunks.add(new ScoredChunk(chunk, similarity));
        }

        scoredChunks.sort(Comparator.comparingDouble(ScoredChunk::similarity).reversed());

        List<RagEmbeddingSearchResult> results = new ArrayList<>();
        int resultSize = Math.min(topK, scoredChunks.size());
        for (int index = 0; index < resultSize; index++) {
            ScoredChunk scoredChunk = scoredChunks.get(index);
            results.add(new RagEmbeddingSearchResult(
                    index + 1,
                    scoredChunk.similarity(),
                    scoredChunk.chunk().content(),
                    scoredChunk.chunk().metadata()
            ));
        }
        return results;
    }

    private void validateAvailability() {
        if (!ragProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "rag demo is disabled");
        }
        if (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "siliconflow api key is not configured");
        }
    }

    private record DemoChunk(String content, Map<String, String> metadata) {
    }

    private record ScoredChunk(DemoChunk chunk, double similarity) {
    }
}
