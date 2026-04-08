package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.rag.config.RagProperties;
import io.milvus.v2.common.ConsistencyLevel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Embedding retrieval demo service with hybrid coarse retrieval and optional rerank.
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
                    "订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出，承运商顺丰，当前状态运输中。",
                    Map.of("doc_id", "logistics_002", "title", "物流状态", "category", "logistics")
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
    private final SiliconFlowRerankService siliconFlowRerankService;
    private final Optional<MilvusVectorStoreService> milvusVectorStoreService;

    public SiliconFlowEmbeddingDemoService(
            HttpClient ragHttpClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            DemoHashEmbeddingService demoHashEmbeddingService,
            SiliconFlowRerankService siliconFlowRerankService,
            Optional<MilvusVectorStoreService> milvusVectorStoreService
    ) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
        this.demoHashEmbeddingService = demoHashEmbeddingService;
        this.siliconFlowRerankService = siliconFlowRerankService;
        this.milvusVectorStoreService = milvusVectorStoreService;
    }

    public RagEmbeddingSearchResponse search(RagEmbeddingSearchRequest request) {
        validateAvailability();

        String query = request.query().trim();
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        SearchPlan plan = buildSearchPlan(topK);

        List<String> chunkTexts = DEMO_CHUNKS.stream().map(DemoChunk::content).toList();
        List<double[]> chunkVectors = embed(chunkTexts);
        double[] queryVector = embed(query);
        if (chunkVectors.size() != DEMO_CHUNKS.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回条数与 demo chunk 数量不一致");
        }

        int vectorDimension = queryVector.length;
        List<Double> sparseScores = new SimpleBm25Scorer(chunkTexts).scoreAll(query);
        List<CandidateHit> coarseHits = ragProperties.getMilvus().isEnabled()
                ? buildMilvusCandidates(query, queryVector, chunkVectors, sparseScores, plan)
                : buildInMemoryCandidates(queryVector, chunkVectors, sparseScores, plan);

        SiliconFlowRerankService.RerankOutcome rerankOutcome = siliconFlowRerankService.rerank(
                query,
                coarseHits.stream()
                        .map(hit -> new SiliconFlowRerankService.Candidate(
                                hit.id(),
                                hit.content(),
                                hit.metadata(),
                                hit.denseScore(),
                                hit.sparseScore(),
                                hit.coarseScore()))
                        .toList(),
                plan.finalTopK()
        );

        List<RagEmbeddingSearchResult> results = new ArrayList<>();
        List<SiliconFlowRerankService.RerankResult> rankedResults = rerankOutcome.applied()
                ? rerankOutcome.results()
                : siliconFlowRerankService.rerank(query, List.of(), 1).results();
        if (!rerankOutcome.applied()) {
            rankedResults = coarseHits.stream()
                    .sorted(Comparator.comparingDouble(CandidateHit::coarseScore).reversed())
                    .limit(plan.finalTopK())
                    .map(hit -> new SiliconFlowRerankService.RerankResult(
                            new SiliconFlowRerankService.Candidate(
                                    hit.id(), hit.content(), hit.metadata(), hit.denseScore(), hit.sparseScore(), hit.coarseScore()),
                            0,
                            hit.coarseScore()))
                    .toList();
        }

        for (int index = 0; index < rankedResults.size(); index++) {
            SiliconFlowRerankService.RerankResult reranked = rankedResults.get(index);
            SiliconFlowRerankService.Candidate candidate = reranked.candidate();
            Map<String, String> metadata = new LinkedHashMap<>(candidate.metadata());
            metadata.put("dense_score", formatScore(candidate.denseScore()));
            metadata.put("sparse_score", formatScore(candidate.sparseScore()));
            metadata.put("coarse_score", formatScore(candidate.coarseScore()));
            metadata.put("rerank_score", formatScore(reranked.score()));
            metadata.put("recall_mode", plan.mode().name());
            metadata.put("rerank_provider", rerankOutcome.provider());
            results.add(new RagEmbeddingSearchResult(index + 1, reranked.score(), candidate.content(), metadata));
        }

        return new RagEmbeddingSearchResponse(
                query,
                resolveEmbeddingModel(),
                DEMO_CHUNKS.size(),
                vectorDimension,
                plan.mode().name(),
                coarseHits.size(),
                rerankOutcome.applied(),
                rerankOutcome.provider(),
                rerankOutcome.model(),
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
                    "SiliconFlow Embedding 请求失败：HTTP 状态码=" + response.statusCode() + "，响应体=" + response.body()
                );
            }
            return parseEmbeddingResponse(response.body());
        } catch (HttpConnectTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "SiliconFlow Embedding 连接超时（" + ragProperties.getConnectTimeoutSeconds() + " 秒）",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "SiliconFlow Embedding 请求超时（" + ragProperties.getReadTimeoutSeconds() + " 秒）",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "SiliconFlow Embedding 请求失败：" + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow Embedding 请求被中断", exception);
        }
    }

    double[] embed(String text) {
        if (useDemoEmbeddingProvider()) {
            return demoHashEmbeddingService.embed(text);
        }

        List<double[]> embeddings = embed(List.of(text));
        if (embeddings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow Embedding 响应为空");
        }
        return embeddings.get(0);
    }

    List<double[]> parseEmbeddingResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray() || dataArray.size() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow Embedding 响应缺少 data 字段");
        }

        List<double[]> embeddings = new ArrayList<>();
        for (JsonNode item : dataArray) {
            JsonNode embeddingNode = item.path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.size() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow Embedding 响应中存在缺失 embedding 的条目");
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
            throw new IllegalStateException("序列化 SiliconFlow Embedding 请求体失败", exception);
        }
    }

    private List<CandidateHit> buildInMemoryCandidates(double[] queryVector, List<double[]> chunkVectors,
                                                       List<Double> sparseScores, SearchPlan plan) {
        return switch (plan.mode()) {
            case DENSE_ONLY -> selectDenseCandidates(queryVector, chunkVectors, sparseScores, plan.denseCandidateCount());
            case SPARSE_ONLY -> selectSparseCandidates(queryVector, chunkVectors, sparseScores, plan.sparseCandidateCount());
            case HYBRID -> selectHybridCandidates(queryVector, chunkVectors, sparseScores, plan);
        };
    }

    private List<CandidateHit> buildMilvusCandidates(String query, double[] queryVector, List<double[]> chunkVectors,
                                                     List<Double> sparseScores, SearchPlan plan) {
        MilvusVectorStoreService vectorStoreService = milvusVectorStoreService.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Milvus 向量库服务不可用（请检查 app.rag.milvus.enabled 是否已开启）")
        );

        MilvusVectorStoreService.PreparedCollection preparedCollection = vectorStoreService.prepareCollection(queryVector.length);
        if (preparedCollection.created() || vectorStoreService.isCollectionEmpty(preparedCollection.collectionName())) {
            vectorStoreService.insert(preparedCollection.collectionName(), buildMilvusDocuments(), chunkVectors);
        }

        Map<String, CandidateHit> allCandidates = buildCandidateLookup(queryVector, chunkVectors, sparseScores);
        List<MilvusVectorStoreService.SearchHit> coarseSearchHits = switch (plan.mode()) {
            case DENSE_ONLY -> vectorStoreService.denseSearch(
                    preparedCollection.collectionName(), queryVector, plan.denseCandidateCount(),
                    plan.nprobe(), plan.consistencyLevel());
            case SPARSE_ONLY -> vectorStoreService.sparseSearch(
                    preparedCollection.collectionName(), query, plan.sparseCandidateCount(),
                    plan.dropRatioSearch(), plan.consistencyLevel());
            case HYBRID -> vectorStoreService.hybridSearch(
                    preparedCollection.collectionName(), query, queryVector, plan.hybridCandidateCount(),
                    plan.denseCandidateCount(), plan.sparseCandidateCount(), plan.nprobe(),
                    plan.dropRatioSearch(), plan.rrfK(), plan.consistencyLevel());
        };

        return coarseSearchHits.stream()
                .map(hit -> enrichMilvusHit(hit, allCandidates))
                .sorted(Comparator.comparingDouble(CandidateHit::coarseScore).reversed())
                .toList();
    }

    private CandidateHit enrichMilvusHit(MilvusVectorStoreService.SearchHit hit, Map<String, CandidateHit> allCandidates) {
        CandidateHit candidate = allCandidates.get(hit.id());
        if (candidate == null) {
            return new CandidateHit(hit.id(), hit.content(), hit.metadata(), 0, 0, hit.score());
        }
        return new CandidateHit(candidate.id(), candidate.content(), candidate.metadata(),
                candidate.denseScore(), candidate.sparseScore(), hit.score());
    }

    private Map<String, CandidateHit> buildCandidateLookup(double[] queryVector, List<double[]> chunkVectors, List<Double> sparseScores) {
        Map<String, CandidateHit> lookup = new LinkedHashMap<>();
        for (int index = 0; index < DEMO_CHUNKS.size(); index++) {
            DemoChunk chunk = DEMO_CHUNKS.get(index);
            String id = buildChunkId(index);
            lookup.put(id, new CandidateHit(
                    id,
                    chunk.content(),
                    new LinkedHashMap<>(chunk.metadata()),
                    CosineSimilarity.calculate(queryVector, chunkVectors.get(index)),
                    sparseScores.get(index),
                    0
            ));
        }
        return lookup;
    }

    private List<CandidateHit> selectDenseCandidates(double[] queryVector, List<double[]> chunkVectors,
                                                     List<Double> sparseScores, int candidateCount) {
        return buildScoredCandidates(queryVector, chunkVectors, sparseScores).stream()
                .sorted(Comparator.comparingDouble(CandidateHit::denseScore).reversed())
                .limit(candidateCount)
                .map(hit -> hit.withCoarseScore(hit.denseScore()))
                .toList();
    }

    private List<CandidateHit> selectSparseCandidates(double[] queryVector, List<double[]> chunkVectors,
                                                      List<Double> sparseScores, int candidateCount) {
        return buildScoredCandidates(queryVector, chunkVectors, sparseScores).stream()
                .sorted(Comparator.comparingDouble(CandidateHit::sparseScore).reversed())
                .limit(candidateCount)
                .map(hit -> hit.withCoarseScore(hit.sparseScore()))
                .toList();
    }

    private List<CandidateHit> selectHybridCandidates(double[] queryVector, List<double[]> chunkVectors,
                                                      List<Double> sparseScores, SearchPlan plan) {
        List<CandidateHit> allCandidates = buildScoredCandidates(queryVector, chunkVectors, sparseScores);
        List<CandidateHit> denseHits = allCandidates.stream()
                .sorted(Comparator.comparingDouble(CandidateHit::denseScore).reversed())
                .limit(plan.denseCandidateCount())
                .toList();
        List<CandidateHit> sparseHits = allCandidates.stream()
                .sorted(Comparator.comparingDouble(CandidateHit::sparseScore).reversed())
                .limit(plan.sparseCandidateCount())
                .toList();

        Map<String, Double> fusionScores = new LinkedHashMap<>();
        for (int index = 0; index < denseHits.size(); index++) {
            fusionScores.merge(denseHits.get(index).id(), 1.0 / (plan.rrfK() + index + 1), Double::sum);
        }
        for (int index = 0; index < sparseHits.size(); index++) {
            fusionScores.merge(sparseHits.get(index).id(), 1.0 / (plan.rrfK() + index + 1), Double::sum);
        }

        return allCandidates.stream()
                .filter(hit -> fusionScores.containsKey(hit.id()))
                .map(hit -> hit.withCoarseScore(fusionScores.get(hit.id())))
                .sorted(Comparator.comparingDouble(CandidateHit::coarseScore).reversed())
                .limit(plan.hybridCandidateCount())
                .toList();
    }

    private List<CandidateHit> buildScoredCandidates(double[] queryVector, List<double[]> chunkVectors, List<Double> sparseScores) {
        List<CandidateHit> candidates = new ArrayList<>();
        for (int index = 0; index < DEMO_CHUNKS.size(); index++) {
            DemoChunk chunk = DEMO_CHUNKS.get(index);
            candidates.add(new CandidateHit(
                    buildChunkId(index),
                    chunk.content(),
                    new LinkedHashMap<>(chunk.metadata()),
                    CosineSimilarity.calculate(queryVector, chunkVectors.get(index)),
                    sparseScores.get(index),
                    0
            ));
        }
        return candidates;
    }

    private SearchPlan buildSearchPlan(int finalTopK) {
        RagProperties.RetrievalProperties retrieval = ragProperties.getRetrieval();
        return new SearchPlan(
                retrieval.getMode(),
                Math.max(finalTopK, retrieval.getDenseRecallTopK()),
                Math.max(finalTopK, retrieval.getSparseRecallTopK()),
                finalTopK,
                retrieval.getNprobe(),
                retrieval.getDropRatioSearch(),
                retrieval.getRrfK(),
                retrieval.resolveConsistencyLevel()
        );
    }

    private List<MilvusVectorStoreService.ChunkDocument> buildMilvusDocuments() {
        List<MilvusVectorStoreService.ChunkDocument> documents = new ArrayList<>(DEMO_CHUNKS.size());
        for (int index = 0; index < DEMO_CHUNKS.size(); index++) {
            DemoChunk chunk = DEMO_CHUNKS.get(index);
            documents.add(new MilvusVectorStoreService.ChunkDocument(
                    buildChunkId(index),
                    chunk.content(),
                    chunk.metadata().getOrDefault("doc_id", ""),
                    chunk.metadata().getOrDefault("title", ""),
                    chunk.metadata().getOrDefault("category", "general")
            ));
        }
        return documents;
    }

    private String buildChunkId(int index) {
        return "demo_chunk_" + (index + 1);
    }

    private void validateAvailability() {
        if (!ragProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG 演示功能未启用");
        }
        if (!useDemoEmbeddingProvider() && (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SiliconFlow API Key 未配置");
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

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.6f", score);
    }

    private record DemoChunk(String content, Map<String, String> metadata) {
    }

    private record CandidateHit(String id, String content, Map<String, String> metadata,
                                double denseScore, double sparseScore, double coarseScore) {
        private CandidateHit withCoarseScore(double nextCoarseScore) {
            return new CandidateHit(id, content, metadata, denseScore, sparseScore, nextCoarseScore);
        }
    }

    private record SearchPlan(RagProperties.SearchMode mode, int denseCandidateCount, int sparseCandidateCount,
                              int finalTopK, int nprobe, double dropRatioSearch, int rrfK,
                              ConsistencyLevel consistencyLevel) {
        private int hybridCandidateCount() {
            return Math.max(finalTopK, Math.max(denseCandidateCount, sparseCandidateCount));
        }
    }
}
