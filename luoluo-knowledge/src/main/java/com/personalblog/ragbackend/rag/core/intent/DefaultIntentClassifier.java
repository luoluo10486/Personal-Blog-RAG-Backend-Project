package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DefaultIntentClassifier implements IntentClassifier, IntentNodeRegistry {
    private static final int MAX_INTENT_COUNT = 8;
    private static final double MIN_SCORE = 0.15D;

    private final RagIntentCatalogService catalogService;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;

    public DefaultIntentClassifier(RagIntentCatalogService catalogService,
                                   LLMService llmService,
                                   ObjectMapper objectMapper,
                                   PromptTemplateLoader promptTemplateLoader) {
        this.catalogService = catalogService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    @Override
    public List<NodeScore> classifyTargets(String question) {
        List<RagIntentNode> leafNodes = catalogService.listLeafNodes();
        if (CollUtil.isEmpty(leafNodes) || StrUtil.isBlank(question)) {
            return List.of();
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
        return List.of();
    }

    @Override
    public IntentNode getNodeById(String id) {
        RagIntentNode node = catalogService.findByIntentCode(id);
        if (node == null) {
            return null;
        }
        return toIntentNode(node);
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
                    .filter(each -> id.equals(each.getIntentCode()))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                continue;
            }
            String reason = item.hasNonNull("reason") ? item.get("reason").asText() : null;
            scores.add(new NodeScore(node, score, reason));
        }
        return scores;
    }

    private List<NodeScore> capAndSort(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .limit(MAX_INTENT_COUNT)
                .toList();
    }

    private String buildPrompt(List<RagIntentNode> leafNodes) {
        StringBuilder builder = new StringBuilder();
        for (RagIntentNode node : leafNodes) {
            builder.append("- id=").append(node.getIntentCode()).append("\n");
            builder.append("  path=").append(StrUtil.blankToDefault(node.getFullPath(), node.getName())).append("\n");
            builder.append("  description=").append(StrUtil.blankToDefault(node.getDescription(), "")).append("\n");
            builder.append("  kind=").append(node.getKindCode() == null ? 0 : node.getKindCode()).append("\n");
            if (StrUtil.isNotBlank(node.getCollectionName())) {
                builder.append("  collection=").append(node.getCollectionName()).append("\n");
            }
            if (StrUtil.isNotBlank(node.getMcpToolId())) {
                builder.append("  mcpToolId=").append(node.getMcpToolId()).append("\n");
            }
            if (CollUtil.isNotEmpty(node.getExamples())) {
                builder.append("  examples=").append(String.join(" / ", node.getExamples())).append("\n");
            }
            builder.append("\n");
        }
        return promptTemplateLoader.render(
                "prompt/intent-classifier.st",
                java.util.Map.of("intent_list", builder.toString().trim())
        );
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }

    private IntentNode toIntentNode(RagIntentNode source) {
        IntentNode target = new IntentNode();
        target.setId(source.getIntentCode());
        target.setName(source.getName());
        target.setLevel(source.getLevel());
        target.setParentId(source.getParentCode());
        target.setDescription(source.getDescription());
        target.setExamples(source.getExamples());
        target.setCollectionName(source.getCollectionName());
        target.setTopK(source.getTopK());
        target.setMcpToolId(source.getMcpToolId());
        target.setKind(source.getKind());
        target.setPromptSnippet(source.getPromptSnippet());
        target.setPromptTemplate(source.getPromptTemplate());
        target.setParamPromptTemplate(source.getParamPromptTemplate());
        target.setSortOrder(source.getSortOrder());
        target.setFullPath(source.getFullPath());
        target.setKbId(source.getKbId());
        target.setChildren(source.getChildren());
        return target;
    }
}
