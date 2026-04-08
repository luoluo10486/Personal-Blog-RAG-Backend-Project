package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 二阶段重排序服务：对粗召回候选集进行重排。
 *
 * 支持：
 * - SiliconFlow rerank API
 * - 本地启发式兜底（当未配置/调用失败时）
 */
@Service
public class SiliconFlowRerankService {
    private static final Logger log = LoggerFactory.getLogger(SiliconFlowRerankService.class);
    private static final String DEMO_MODEL = "heuristic-rerank-v1";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public SiliconFlowRerankService(HttpClient ragHttpClient, ObjectMapper objectMapper, RagProperties ragProperties) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    public RerankOutcome rerank(String query, List<Candidate> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return new RerankOutcome(false, "未启用", "", List.of());
        }

        int limitedTopN = Math.max(1, Math.min(topN, candidates.size()));
        if (!ragProperties.getRerank().isEnabled()) {
            return new RerankOutcome(false, "未启用", "", passThrough(candidates, limitedTopN));
        }

        if (usesSiliconFlowProvider() && hasApiKey()) {
            try {
                return new RerankOutcome(true, "siliconflow", ragProperties.getRerank().getModel(),
                        rerankBySiliconFlow(query, candidates, limitedTopN));
            } catch (RuntimeException exception) {
                log.warn("SiliconFlow 重排序调用失败，已回退到本地启发式重排: {}", exception.getMessage());
            }
        }

        return new RerankOutcome(true, "demo", DEMO_MODEL, rerankByHeuristic(query, candidates, limitedTopN));
    }

    private List<RerankResult> rerankBySiliconFlow(String query, List<Candidate> candidates, int topN) {
        HttpRequest request = buildRerankHttpRequest(query, candidates, topN);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "SiliconFlow 重排序请求失败：HTTP 状态码=" + response.statusCode() + "，响应体=" + response.body());
            }
            return parseRerankResponse(response.body(), candidates, topN);
        } catch (HttpConnectTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "SiliconFlow 重排序连接超时（" + ragProperties.getConnectTimeoutSeconds() + " 秒）",
                    exception);
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "SiliconFlow 重排序请求超时（" + ragProperties.getReadTimeoutSeconds() + " 秒）",
                    exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "SiliconFlow 重排序请求失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow 重排序请求被中断", exception);
        }
    }

    private HttpRequest buildRerankHttpRequest(String query, List<Candidate> candidates, int topN) {
        return HttpRequest.newBuilder()
                .uri(URI.create(ragProperties.getRerank().getApiUrl()))
                .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRerankRequestJson(query, candidates, topN)))
                .build();
    }

    private String buildRerankRequestJson(String query, List<Candidate> candidates, int topN) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ragProperties.getRerank().getModel());
        requestBody.put("query", query);
        requestBody.put("top_n", topN);
        requestBody.put("return_documents", true);
        ArrayNode documents = requestBody.putArray("documents");
        for (Candidate candidate : candidates) {
            documents.add(candidate.content());
        }
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (IOException exception) {
            throw new IllegalStateException("序列化 SiliconFlow 重排序请求体失败", exception);
        }
    }

    private List<RerankResult> parseRerankResponse(String responseBody, List<Candidate> candidates, int topN) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SiliconFlow 重排序响应缺少 results 字段");
        }

        List<RerankResult> results = new ArrayList<>();
        for (JsonNode item : resultsNode) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            double score = item.path("relevance_score").asDouble(0);
            Candidate candidate = candidates.get(index);
            results.add(new RerankResult(candidate, 0, score));
        }

        results.sort(Comparator.comparingDouble(RerankResult::score).reversed());
        List<RerankResult> limited = new ArrayList<>();
        for (int index = 0; index < Math.min(topN, results.size()); index++) {
            RerankResult result = results.get(index);
            limited.add(new RerankResult(result.candidate(), index + 1, result.score()));
        }
        return limited;
    }

    private List<RerankResult> rerankByHeuristic(String query, List<Candidate> candidates, int topN) {
        List<String> queryTokens = TextTokenUtils.tokenize(query);
        Set<String> queryIdentifiers = TextTokenUtils.extractIdentifierTokens(query);

        double minDense = candidates.stream().mapToDouble(Candidate::denseScore).min().orElse(0);
        double maxDense = candidates.stream().mapToDouble(Candidate::denseScore).max().orElse(0);
        double minSparse = candidates.stream().mapToDouble(Candidate::sparseScore).min().orElse(0);
        double maxSparse = candidates.stream().mapToDouble(Candidate::sparseScore).max().orElse(0);
        double minCoarse = candidates.stream().mapToDouble(Candidate::coarseScore).min().orElse(0);
        double maxCoarse = candidates.stream().mapToDouble(Candidate::coarseScore).max().orElse(0);

        List<RerankResult> scored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            List<String> candidateTokens = TextTokenUtils.tokenize(candidate.content());
            double tokenCoverage = TextTokenUtils.overlapRatio(queryTokens, candidateTokens);
            double denseScore = normalize(candidate.denseScore(), minDense, maxDense);
            double sparseScore = normalize(candidate.sparseScore(), minSparse, maxSparse);
            double coarseScore = normalize(candidate.coarseScore(), minCoarse, maxCoarse);
            double identifierBoost = identifierBoost(queryIdentifiers, candidate.content());
            double literalBoost = TextTokenUtils.containsQueryLiteral(query, candidate.content()) ? 0.2 : 0.0;

            double finalScore = 0.4 * denseScore
                    + 0.25 * sparseScore
                    + 0.15 * coarseScore
                    + 0.2 * tokenCoverage
                    + identifierBoost
                    + literalBoost;
            scored.add(new RerankResult(candidate, 0, finalScore));
        }

        scored.sort(Comparator.comparingDouble(RerankResult::score).reversed());
        List<RerankResult> limited = new ArrayList<>();
        for (int index = 0; index < Math.min(topN, scored.size()); index++) {
            RerankResult result = scored.get(index);
            limited.add(new RerankResult(result.candidate(), index + 1, result.score()));
        }
        return limited;
    }

    private List<RerankResult> passThrough(List<Candidate> candidates, int topN) {
        List<Candidate> limitedCandidates = new ArrayList<>(candidates);
        limitedCandidates.sort(Comparator.comparingDouble(Candidate::coarseScore).reversed());
        List<RerankResult> results = new ArrayList<>();
        for (int index = 0; index < Math.min(topN, limitedCandidates.size()); index++) {
            Candidate candidate = limitedCandidates.get(index);
            results.add(new RerankResult(candidate, index + 1, candidate.coarseScore()));
        }
        return results;
    }

    private double identifierBoost(Set<String> queryIdentifiers, String candidateText) {
        if (queryIdentifiers.isEmpty()) {
            return 0;
        }
        Set<String> candidateIdentifiers = new LinkedHashSet<>(TextTokenUtils.extractIdentifierTokens(candidateText));
        for (String queryIdentifier : queryIdentifiers) {
            if (candidateIdentifiers.contains(queryIdentifier)) {
                return 0.35;
            }
        }
        return 0;
    }

    private double normalize(double value, double min, double max) {
        if (Double.compare(max, min) == 0) {
            return value > 0 ? 1.0 : 0.0;
        }
        return (value - min) / (max - min);
    }

    private boolean usesSiliconFlowProvider() {
        return "siliconflow".equalsIgnoreCase(ragProperties.getRerank().getProvider());
    }

    private boolean hasApiKey() {
        return ragProperties.getApiKey() != null && !ragProperties.getApiKey().isBlank();
    }

    public record Candidate(String id, String content, Map<String, String> metadata,
                            double denseScore, double sparseScore, double coarseScore) {
    }

    public record RerankResult(Candidate candidate, int rank, double score) {
    }

    public record RerankOutcome(boolean applied, String provider, String model, List<RerankResult> results) {
    }
}
