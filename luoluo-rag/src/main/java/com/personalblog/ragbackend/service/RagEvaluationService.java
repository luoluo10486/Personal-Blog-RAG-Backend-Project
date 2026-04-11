package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagEvaluationCase;
import com.personalblog.ragbackend.dto.rag.RagEvaluationCaseResult;
import com.personalblog.ragbackend.dto.rag.RagEvaluationRequest;
import com.personalblog.ragbackend.dto.rag.RagEvaluationResponse;
import com.personalblog.ragbackend.dto.rag.RagEvaluationScore;
import com.personalblog.ragbackend.dto.rag.RagEvaluationSummary;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 评测服务：评估入口路由、检索与生成质量。
 */
@Service
public class RagEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);

    private final IntentClassifierService intentClassifierService;
    private final SiliconFlowEmbeddingDemoService embeddingDemoService;
    private final RagGenerationDemoService ragGenerationDemoService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public RagEvaluationService(
            IntentClassifierService intentClassifierService,
            SiliconFlowEmbeddingDemoService embeddingDemoService,
            RagGenerationDemoService ragGenerationDemoService,
            HttpClient ragHttpClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties
    ) {
        this.intentClassifierService = intentClassifierService;
        this.embeddingDemoService = embeddingDemoService;
        this.ragGenerationDemoService = ragGenerationDemoService;
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    public RagEvaluationResponse evaluate(RagEvaluationRequest request) {
        List<RagEvaluationCase> cases = request.cases().isEmpty() ? buildDefaultDataset() : request.cases();
        boolean runJudge = Boolean.TRUE.equals(request.runJudge());
        int topK = request.topK() == null ? 3 : request.topK();

        List<RagEvaluationCaseResult> caseResults = new ArrayList<>(cases.size());
        for (RagEvaluationCase evalCase : cases) {
            caseResults.add(evaluateCase(evalCase, topK, runJudge));
        }

        return new RagEvaluationResponse(buildSummary(caseResults), caseResults);
    }

    List<RagEvaluationCase> buildDefaultDataset() {
        return List.of(
                new RagEvaluationCase(
                        "退货政策是什么？",
                        "签收后 7 天内，未使用且不影响二次销售的商品支持无理由退货，退货运费由用户承担，若商品存在质量问题则由商家承担。",
                        List.of("policy_001"),
                        "knowledge"
                ),
                new RagEvaluationCase(
                        "退货运费谁承担？",
                        "退货运费由用户承担，若商品存在质量问题则由商家承担。",
                        List.of("policy_001"),
                        "knowledge"
                ),
                new RagEvaluationCase(
                        "订单发货后多久能看到物流信息？",
                        "订单发货后 24 小时内会更新物流信息，用户可在订单详情页查看配送进度。",
                        List.of("logistics_001"),
                        "knowledge"
                ),
                new RagEvaluationCase(
                        "会员积分怎么抵扣现金？",
                        "会员积分可在结算时抵扣现金，每 100 积分可抵扣 1 元，单笔订单最多抵扣订单金额的 50%。",
                        List.of("member_001"),
                        "knowledge"
                ),
                new RagEvaluationCase(
                        "生鲜商品支持 7 天无理由退货吗？",
                        "生鲜商品不支持 7 天无理由退货，如有质量问题需在签收后 48 小时内反馈。",
                        List.of("policy_002"),
                        "knowledge"
                ),
                new RagEvaluationCase(
                        "你好",
                        "你好，很高兴为你服务。",
                        List.of(),
                        "chitchat"
                ),
                new RagEvaluationCase(
                        "有什么推荐的",
                        "我还不能确定您的具体需求，请补充更明确的信息。",
                        List.of(),
                        "clarification"
                ),
                new RagEvaluationCase(
                        "查一下我的订单状态",
                        "这个问题更适合走工具调用链路。",
                        List.of(),
                        "tool"
                )
        );
    }

    private RagEvaluationCaseResult evaluateCase(RagEvaluationCase evalCase, int topK, boolean runJudge) {
        String query = safeText(evalCase.query());
        IntentClassifierService.IntentResult intentResult = intentClassifierService.classify(List.of(), query);
        String predictedIntent = intentResult.intent();
        boolean routeMatched = equalsIgnoreCase(evalCase.expectedIntent(), predictedIntent);

        List<String> retrievedDocIds = List.of();
        boolean hit = false;
        double reciprocalRank = 0.0;
        String chunksText = "（未检索到相关内容）";

        if ("knowledge".equalsIgnoreCase(predictedIntent)) {
            RagEmbeddingSearchResponse retrieval = embeddingDemoService.search(new RagEmbeddingSearchRequest(query, topK));
            retrievedDocIds = retrieval.results().stream()
                    .map(result -> result.metadata().getOrDefault("doc_id", ""))
                    .toList();
            hit = calculateHit(retrievedDocIds, evalCase.relevantDocIds());
            reciprocalRank = calculateReciprocalRank(retrievedDocIds, evalCase.relevantDocIds());
            chunksText = retrieval.results().stream()
                    .map(result -> {
                        String docId = result.metadata().getOrDefault("doc_id", "");
                        return "[" + docId + "] " + result.content();
                    })
                    .collect(Collectors.joining("\n"));
            if (chunksText.isBlank()) {
                chunksText = "（未检索到相关内容）";
            }
        }

        RagGenerationResponse generation = ragGenerationDemoService.generate(new RagGenerationRequest(query, topK, null));
        RagEvaluationScore faithfulness = runJudge && "knowledge".equalsIgnoreCase(predictedIntent)
                ? scoreFaithfulness(chunksText, generation.answer())
                : null;
        RagEvaluationScore relevancy = runJudge
                ? scoreRelevancy(query, generation.answer())
                : null;
        RagEvaluationScore correctness = runJudge
                ? scoreCorrectness(query, safeText(evalCase.expectedAnswer()), generation.answer())
                : null;

        return new RagEvaluationCaseResult(
                query,
                safeText(evalCase.expectedIntent()),
                predictedIntent,
                routeMatched,
                retrievedDocIds,
                hit,
                reciprocalRank,
                generation.answer(),
                generation.functionCallApplied(),
                generation.calledTools(),
                faithfulness,
                relevancy,
                correctness,
                determineRootCause(routeMatched, hit, faithfulness, correctness)
        );
    }

    private RagEvaluationSummary buildSummary(List<RagEvaluationCaseResult> results) {
        int totalCases = results.size();
        double intentAccuracy = averageRatio(results.stream().filter(RagEvaluationCaseResult::routeMatched).count(), totalCases);

        List<RagEvaluationCaseResult> retrievalResults = results.stream()
                .filter(result -> !result.retrievedDocIds().isEmpty() || "knowledge".equalsIgnoreCase(result.expectedIntent()))
                .toList();
        long hitCount = retrievalResults.stream().filter(RagEvaluationCaseResult::hit).count();
        double hitRate = retrievalResults.isEmpty() ? 0.0 : (double) hitCount / retrievalResults.size();
        double mrr = retrievalResults.stream().mapToDouble(RagEvaluationCaseResult::reciprocalRank).average().orElse(0.0);

        double avgFaithfulness = averageScore(results.stream().map(RagEvaluationCaseResult::faithfulness).toList());
        double avgRelevancy = averageScore(results.stream().map(RagEvaluationCaseResult::relevancy).toList());
        double avgCorrectness = averageScore(results.stream().map(RagEvaluationCaseResult::correctness).toList());

        long correctnessHighCount = results.stream()
                .filter(result -> result.correctness() != null && result.correctness().score() >= 4)
                .count();
        double correctRate = averageRatio(correctnessHighCount, totalCases);

        long fallbackCount = results.stream()
                .filter(result -> containsFallback(result.actualAnswer()))
                .count();
        double fallbackRate = averageRatio(fallbackCount, totalCases);

        long hallucinationCount = results.stream()
                .filter(result -> result.faithfulness() != null && result.faithfulness().score() <= 2)
                .count();
        double hallucinationRate = averageRatio(hallucinationCount, totalCases);

        return new RagEvaluationSummary(
                totalCases,
                intentAccuracy,
                hitRate,
                mrr,
                avgFaithfulness,
                avgRelevancy,
                avgCorrectness,
                correctRate,
                fallbackRate,
                hallucinationRate
        );
    }

    boolean calculateHit(List<String> retrievedIds, List<String> relevantIds) {
        if (relevantIds == null || relevantIds.isEmpty()) {
            return false;
        }
        for (String id : retrievedIds) {
            if (relevantIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    double calculateReciprocalRank(List<String> retrievedIds, List<String> relevantIds) {
        if (relevantIds == null || relevantIds.isEmpty()) {
            return 0.0;
        }
        for (int index = 0; index < retrievedIds.size(); index++) {
            if (relevantIds.contains(retrievedIds.get(index))) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }

    private RagEvaluationScore scoreFaithfulness(String chunks, String answer) {
        String prompt = """
                你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否忠实于给定的参考文档内容。

                评分标准：
                - 5 分：回答完全基于参考文档，没有添加任何文档中没有的信息
                - 4 分：回答基本基于参考文档，有极少量合理推断但不影响准确性
                - 3 分：回答部分基于参考文档，但添加了一些文档中没有的信息
                - 2 分：回答包含较多文档中没有的信息，存在明显编造
                - 1 分：回答与参考文档内容严重不符或大量编造

                参考文档内容：
                %s

                模型的回答：
                %s

                请按以下 JSON 格式输出评分结果，不要输出其他内容：
                {"score": 1, "label": "faithful", "reason": "简要说明"}
                """.formatted(chunks, answer);
        return llmScore(prompt);
    }

    private RagEvaluationScore scoreRelevancy(String query, String answer) {
        String prompt = """
                你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否回答了用户的问题。

                评分标准：
                - 5 分：直接、完整地回答了用户的问题
                - 4 分：回答了用户的问题，但不够完整或包含了多余信息
                - 3 分：部分回答了用户的问题，但遗漏了关键信息
                - 2 分：回答与用户的问题有关，但没有真正回答问题
                - 1 分：回答与用户的问题完全无关

                用户问题：
                %s

                模型的回答：
                %s

                请按以下 JSON 格式输出评分结果，不要输出其他内容：
                {"score": 1, "label": "relevant", "reason": "简要说明"}
                """.formatted(query, answer);
        return llmScore(prompt);
    }

    private RagEvaluationScore scoreCorrectness(String query, String expectedAnswer, String actualAnswer) {
        String prompt = """
                你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否正确。

                评分标准：
                - 5 分：回答与标准答案的含义完全一致
                - 4 分：回答与标准答案基本一致，核心信息正确，细节略有差异
                - 3 分：回答部分正确，但遗漏或错误了一些重要信息
                - 2 分：回答包含正确信息，但主要结论有误
                - 1 分：回答与标准答案完全不一致

                用户问题：
                %s

                标准答案：
                %s

                模型的回答：
                %s

                请按以下 JSON 格式输出评分结果，不要输出其他内容：
                {"score": 1, "label": "correct", "reason": "简要说明"}
                """.formatted(query, expectedAnswer, actualAnswer);
        return llmScore(prompt);
    }

    private RagEvaluationScore llmScore(String scorePrompt) {
        if (ragProperties.getApiKey() == null || ragProperties.getApiKey().isBlank()) {
            return null;
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", ragProperties.getEvaluation().getJudgeModel());
            requestBody.put("temperature", ragProperties.getEvaluation().getJudgeTemperature());
            requestBody.put("max_tokens", ragProperties.getEvaluation().getJudgeMaxTokens());

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", scorePrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ragProperties.getApiUrl()))
                    .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Judge model HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            String jsonText = extractJsonObject(content);
            if (jsonText == null) {
                throw new IOException("Judge model 未返回可解析 JSON: " + content);
            }

            JsonNode scoreNode = objectMapper.readTree(jsonText);
            return new RagEvaluationScore(
                    scoreNode.path("score").asInt(0),
                    scoreNode.path("label").asText(""),
                    scoreNode.path("reason").asText("")
            );
        } catch (Exception exception) {
            log.warn("Judge 评分失败: {}", exception.getMessage());
            return null;
        }
    }

    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    private String determineRootCause(
            boolean routeMatched,
            boolean hit,
            RagEvaluationScore faithfulness,
            RagEvaluationScore correctness
    ) {
        if (!routeMatched) {
            return "路由问题";
        }
        if (!hit) {
            return "检索问题";
        }
        if (faithfulness != null && faithfulness.score() > 0 && faithfulness.score() <= 3) {
            return "生成问题";
        }
        if (correctness != null && correctness.score() > 0 && correctness.score() < 4) {
            return "知识库问题";
        }
        return "无明显问题";
    }

    private double averageScore(List<RagEvaluationScore> scores) {
        return scores.stream()
                .filter(score -> score != null && score.score() > 0)
                .mapToInt(RagEvaluationScore::score)
                .average()
                .orElse(0.0);
    }

    private double averageRatio(long numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private boolean containsFallback(String answer) {
        String text = safeText(answer);
        return text.contains("抱歉") || text.contains("找不到") || text.contains("没有找到") || text.contains("建议您联系人工客服");
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return safeText(left).equalsIgnoreCase(safeText(right));
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
