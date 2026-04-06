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
import java.util.Optional;

/**
 * Embedding 检索演示服务。
 */
@Service
public class SiliconFlowEmbeddingDemoService {
    private static final int DEFAULT_TOP_K = 3;

    private static final List<DemoChunk> DEMO_CHUNKS = List.of(
            new DemoChunk(
                    "签收后 7 天内，未使用且不影响二次销售的商品支持无理由退货。",
                    Map.of("doc_id", "policy_001", "title", "退货政策", "category", "return_policy")
            ),
            new DemoChunk(
                    "退货运费由用户承担，若商品存在质量问题则由商家承担。",
                    Map.of("doc_id", "policy_001", "title", "退货政策", "category", "return_policy")
            ),
            new DemoChunk(
                    "订单发货后 24 小时内会更新物流信息，用户可在订单详情页查看配送进度。",
                    Map.of("doc_id", "logistics_001", "title", "物流说明", "category", "logistics")
            ),
            new DemoChunk(
                    "会员积分可在结算时抵扣现金，每 100 积分可抵扣 1 元，单笔订单最多抵扣订单金额的 50%。",
                    Map.of("doc_id", "member_001", "title", "会员权益", "category", "membership")
            ),
            new DemoChunk(
                    "生鲜商品不支持 7 天无理由退货，如有质量问题需在签收后 48 小时内反馈。",
                    Map.of("doc_id", "policy_002", "title", "生鲜退货政策", "category", "return_policy")
            )
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;
    private final DemoHashEmbeddingService demoHashEmbeddingService;
    private final Optional<MilvusVectorStoreService> milvusVectorStoreService;

    public SiliconFlowEmbeddingDemoService(
            HttpClient ragHttpClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            DemoHashEmbeddingService demoHashEmbeddingService,
            Optional<MilvusVectorStoreService> milvusVectorStoreService
    ) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
        this.demoHashEmbeddingService = demoHashEmbeddingService;
        this.milvusVectorStoreService = milvusVectorStoreService;
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
                    "embedding response size does not match demo chunks"
            );
        }

        int vectorDimension = queryVector.length;
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        List<RagEmbeddingSearchResult> results = ragProperties.getMilvus().isEnabled()
                ? buildMilvusResults(queryVector, chunkVectors, topK)
                : buildInMemoryResults(queryVector, chunkVectors, topK);

        return new RagEmbeddingSearchResponse(
                request.query().trim(),
                resolveEmbeddingModel(),
                DEMO_CHUNKS.size(),
                vectorDimension,
                results
        );
    }

    List<double[]> embed(List<String> texts) {
        if (useDemoEmbeddingProvider()) {
            return demoHashEmbeddingService.embed(texts);
        }

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
        if (useDemoEmbeddingProvider()) {
            return demoHashEmbeddingService.embed(text);
        }

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

    private List<RagEmbeddingSearchResult> buildInMemoryResults(double[] queryVector, List<double[]> chunkVectors, int topK) {
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

    private List<RagEmbeddingSearchResult> buildMilvusResults(double[] queryVector, List<double[]> chunkVectors, int topK) {
        MilvusVectorStoreService vectorStoreService = milvusVectorStoreService.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "milvus vector store is not available")
        );

        MilvusVectorStoreService.PreparedCollection preparedCollection = vectorStoreService.prepareCollection(queryVector.length);
        if (preparedCollection.created() || vectorStoreService.isCollectionEmpty(preparedCollection.collectionName())) {
            vectorStoreService.insert(preparedCollection.collectionName(), buildMilvusDocuments(), chunkVectors);
        }

        List<RagEmbeddingSearchResult> results = new ArrayList<>();
        for (MilvusVectorStoreService.SearchHit hit : vectorStoreService.search(preparedCollection.collectionName(), queryVector, topK)) {
            results.add(new RagEmbeddingSearchResult(
                    hit.rank(),
                    hit.score(),
                    hit.content(),
                    hit.metadata()
            ));
        }
        return results;
    }

    private List<MilvusVectorStoreService.ChunkDocument> buildMilvusDocuments() {
        List<MilvusVectorStoreService.ChunkDocument> documents = new ArrayList<>(DEMO_CHUNKS.size());
        for (int index = 0; index < DEMO_CHUNKS.size(); index++) {
            DemoChunk chunk = DEMO_CHUNKS.get(index);
            documents.add(new MilvusVectorStoreService.ChunkDocument(
                    "demo_chunk_" + (index + 1),
                    chunk.content(),
                    chunk.metadata().getOrDefault("doc_id", ""),
                    chunk.metadata().getOrDefault("title", ""),
                    chunk.metadata().getOrDefault("category", "general")
            ));
        }
        return documents;
    }

    private void validateAvailability() {
        if (!ragProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "rag demo is disabled");
        }
        if (!useDemoEmbeddingProvider() && (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "siliconflow api key is not configured");
        }
    }

    private boolean useDemoEmbeddingProvider() {
        return "demo".equalsIgnoreCase(ragProperties.getEmbeddingProvider());
    }

    private String resolveEmbeddingModel() {
        if (useDemoEmbeddingProvider()) {
            return demoHashEmbeddingService.modelName();
        }
        return ragProperties.getEmbeddingModel();
    }

    private record DemoChunk(String content, Map<String, String> metadata) {
    }

    private record ScoredChunk(DemoChunk chunk, double similarity) {
    }
}
