package com.personalblog.ragbackend.knowledge.service.rag.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class RagIntentResolver {
    private static final String INTENT_CLASSIFIER_PROMPT_PATH = "prompt/intent-classifier.st";
    private static final int MAX_INTENT_COUNT = 8;
    private static final double MIN_SCORE = 0.15D;

    private final RagIntentCatalogService catalogService;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final Executor intentClassifyExecutor;

    public RagIntentResolver(RagIntentCatalogService catalogService,
                             ObjectProvider<LLMService> llmServiceProvider,
                             ObjectMapper objectMapper,
                             PromptTemplateLoader promptTemplateLoader,
                             @Qualifier("intentClassifyThreadPoolExecutor") Executor intentClassifyExecutor) {
        this.catalogService = catalogService;
        this.llmServiceProvider = llmServiceProvider;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = promptTemplateLoader;
        this.intentClassifyExecutor = intentClassifyExecutor;
    }

    @RagTraceNode(name = "intent-resolve", type = "INTENT")
    public List<SubQuestionIntent> resolve(String question) {
        return resolve(question, List.of());
    }

    public List<SubQuestionIntent> resolve(String question, List<String> explicitSubQuestions) {
        List<String> subQuestions = normalizeSubQuestions(question, explicitSubQuestions);
        if (CollUtil.isEmpty(subQuestions)) {
            return List.of(new SubQuestionIntent(StrUtil.blankToDefault(question, ""), classify(StrUtil.blankToDefault(question, ""))));
        }
        List<CompletableFuture<SubQuestionIntent>> tasks = subQuestions.stream()
                .map(subQuestion -> CompletableFuture.supplyAsync(
                        () -> new SubQuestionIntent(subQuestion, classify(subQuestion)),
                        intentClassifyExecutor
                ))
                .toList();
        return capTotalIntents(tasks.stream().map(CompletableFuture::join).toList());
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
        return promptTemplateLoader.render(
                INTENT_CLASSIFIER_PROMPT_PATH,
                java.util.Map.of("intent_list", builder.toString().trim())
        );
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

    private List<SubQuestionIntent> capTotalIntents(List<SubQuestionIntent> subIntents) {
        int totalIntents = subIntents.stream()
                .mapToInt(subIntent -> subIntent.nodeScores() == null ? 0 : subIntent.nodeScores().size())
                .sum();
        if (totalIntents <= MAX_INTENT_COUNT) {
            return subIntents;
        }

        List<IntentCandidate> allCandidates = collectAllCandidates(subIntents);
        List<IntentCandidate> guaranteed = selectTopIntentPerSubQuestion(allCandidates, subIntents.size());
        int remaining = Math.max(0, MAX_INTENT_COUNT - guaranteed.size());
        List<IntentCandidate> additional = selectAdditionalIntents(allCandidates, guaranteed, remaining);
        return rebuildSubIntents(subIntents, guaranteed, additional);
    }

    private List<IntentCandidate> collectAllCandidates(List<SubQuestionIntent> subIntents) {
        List<IntentCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < subIntents.size(); i++) {
            List<NodeScore> scores = subIntents.get(i).nodeScores();
            if (CollUtil.isEmpty(scores)) {
                continue;
            }
            for (NodeScore score : scores) {
                candidates.add(new IntentCandidate(i, score));
            }
        }
        candidates.sort(Comparator.comparingDouble((IntentCandidate candidate) -> candidate.nodeScore().score()).reversed());
        return candidates;
    }

    private List<IntentCandidate> selectTopIntentPerSubQuestion(List<IntentCandidate> candidates, int subQuestionCount) {
        List<IntentCandidate> selected = new ArrayList<>();
        boolean[] seen = new boolean[subQuestionCount];
        for (IntentCandidate candidate : candidates) {
            if (!seen[candidate.subQuestionIndex()]) {
                selected.add(candidate);
                seen[candidate.subQuestionIndex()] = true;
            }
            if (selected.size() == subQuestionCount) {
                break;
            }
        }
        return selected;
    }

    private List<IntentCandidate> selectAdditionalIntents(List<IntentCandidate> candidates,
                                                          List<IntentCandidate> selected,
                                                          int remaining) {
        if (remaining <= 0) {
            return List.of();
        }
        List<IntentCandidate> additional = new ArrayList<>();
        for (IntentCandidate candidate : candidates) {
            if (selected.contains(candidate)) {
                continue;
            }
            additional.add(candidate);
            if (additional.size() >= remaining) {
                break;
            }
        }
        return additional;
    }

    private List<SubQuestionIntent> rebuildSubIntents(List<SubQuestionIntent> original,
                                                      List<IntentCandidate> guaranteed,
                                                      List<IntentCandidate> additional) {
        Map<Integer, List<NodeScore>> grouped = new HashMap<>();
        List<IntentCandidate> selected = new ArrayList<>(guaranteed);
        selected.addAll(additional);
        for (IntentCandidate candidate : selected) {
            grouped.computeIfAbsent(candidate.subQuestionIndex(), ignored -> new ArrayList<>())
                    .add(candidate.nodeScore());
        }
        List<SubQuestionIntent> result = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            result.add(new SubQuestionIntent(original.get(i).subQuestion(), grouped.getOrDefault(i, List.of())));
        }
        return result;
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

    private List<String> normalizeSubQuestions(String question, List<String> explicitSubQuestions) {
        if (CollUtil.isNotEmpty(explicitSubQuestions)) {
            return explicitSubQuestions.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .limit(4)
                    .toList();
        }
        return splitSubQuestions(question);
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

    private record IntentCandidate(int subQuestionIndex, NodeScore nodeScore) {
    }
}
