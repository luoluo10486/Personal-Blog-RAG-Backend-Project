package com.personalblog.ragbackend.knowledge.service.rag.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RagIntentResolver {
    private static final int MAX_INTENT_COUNT = 8;
    private static final double MIN_SCORE = 0.15D;

    private final RagIntentCatalogService catalogService;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final ObjectMapper objectMapper;

    public RagIntentResolver(RagIntentCatalogService catalogService,
                             ObjectProvider<LLMService> llmServiceProvider,
                             ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.llmServiceProvider = llmServiceProvider;
        this.objectMapper = objectMapper;
    }

    @RagTraceNode(name = "intent-resolve", type = "INTENT")
    public List<SubQuestionIntent> resolve(String question) {
        List<String> subQuestions = splitSubQuestions(question);
        if (CollUtil.isEmpty(subQuestions)) {
            return List.of(new SubQuestionIntent(StrUtil.blankToDefault(question, ""), classify(StrUtil.blankToDefault(question, ""))));
        }
        List<CompletableFuture<SubQuestionIntent>> tasks = subQuestions.stream()
                .map(subQuestion -> CompletableFuture.supplyAsync(() -> new SubQuestionIntent(subQuestion, classify(subQuestion))))
                .toList();
        return tasks.stream().map(CompletableFuture::join).toList();
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        return catalogService.mergeIntentGroup(subIntents);
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return catalogService.isSystemOnly(nodeScores);
    }

    private List<NodeScore> classify(String question) {
        List<RagIntentNode> leafNodes = catalogService.listLeafNodes();
        if (CollUtil.isEmpty(leafNodes) || StrUtil.isBlank(question)) {
            return List.of();
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallbackScore(question, leafNodes);
        }

        try {
            String prompt = buildPrompt(leafNodes);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(prompt),
                            ChatMessage.user(question)
                    ))
                    .temperature(0.1D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request);
            List<NodeScore> parsed = parseScores(raw, leafNodes);
            if (CollUtil.isNotEmpty(parsed)) {
                return capAndSort(parsed);
            }
        } catch (Exception ignored) {
        }
        return fallbackScore(question, leafNodes);
    }

    private String buildPrompt(List<RagIntentNode> leafNodes) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are an intent classifier. Return a JSON array of objects with fields id, score, reason.");
        builder.append("\nOnly choose ids from the list below.\n\n");
        for (RagIntentNode node : leafNodes) {
            builder.append("- id=").append(node.intentCode).append("\n");
            builder.append("  path=").append(StrUtil.blankToDefault(node.fullPath, node.name)).append("\n");
            builder.append("  description=").append(StrUtil.blankToDefault(node.description, "")).append("\n");
            builder.append("  kind=").append(node.kind == null ? 0 : node.kind).append("\n");
            if (StrUtil.isNotBlank(node.collectionName)) {
                builder.append("  collection=").append(node.collectionName).append("\n");
            }
            if (StrUtil.isNotBlank(node.mcpToolId)) {
                builder.append("  mcpToolId=").append(node.mcpToolId).append("\n");
            }
            if (StrUtil.isNotBlank(node.examples)) {
                builder.append("  examples=").append(node.examples.replace("\n", " ")).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private List<NodeScore> parseScores(String raw, List<RagIntentNode> leafNodes) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String normalized = stripCodeFence(raw);
        JsonNode root = objectMapper.readTree(normalized);
        JsonNode arrayNode = root.isArray() ? root : root.has("results") ? root.get("results") : null;
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<NodeScore> scores = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item == null || !item.hasNonNull("id") || !item.hasNonNull("score")) {
                continue;
            }
            String id = item.get("id").asText();
            double score = item.get("score").asDouble();
            if (score < MIN_SCORE) {
                continue;
            }
            RagIntentNode node = leafNodes.stream()
                    .filter(each -> id.equals(each.intentCode))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                continue;
            }
            scores.add(new NodeScore(node, score, item.hasNonNull("reason") ? item.get("reason").asText() : null));
        }
        return scores;
    }

    private List<NodeScore> fallbackScore(String question, List<RagIntentNode> leafNodes) {
        String normalizedQuestion = normalize(question);
        return leafNodes.stream()
                .map(node -> new NodeScore(node, lexicalScore(normalizedQuestion, node), "fallback"))
                .filter(score -> score.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .limit(MAX_INTENT_COUNT)
                .toList();
    }

    private double lexicalScore(String normalizedQuestion, RagIntentNode node) {
        String text = normalize(StrUtil.blankToDefault(node.fullPath, node.name) + " "
                + StrUtil.blankToDefault(node.description, "") + " "
                + StrUtil.blankToDefault(node.examples, ""));
        if (StrUtil.isBlank(text) || StrUtil.isBlank(normalizedQuestion)) {
            return 0D;
        }
        int hits = 0;
        for (String token : normalizedQuestion.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (text.contains(token)) {
                hits++;
            }
        }
        return Math.min(1D, hits / Math.max(1D, normalizedQuestion.split("\\s+").length));
    }

    private List<NodeScore> capAndSort(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .limit(MAX_INTENT_COUNT)
                .toList();
    }

    private List<String> splitSubQuestions(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        return java.util.Arrays.stream(question.split("[\\n\\r\\?？。!！;；]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .limit(4)
                .collect(Collectors.toList());
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ").trim();
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }
}
