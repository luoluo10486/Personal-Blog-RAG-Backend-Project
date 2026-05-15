package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.rag.dao.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultIntentClassifier implements IntentClassifier, IntentNodeRegistry {
    private static final int MAX_INTENT_COUNT = 8;
    private static final double MIN_SCORE = 0.15D;

    private final LLMService llmService;
    private final IntentNodeMapper intentNodeMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;

    public DefaultIntentClassifier(LLMService llmService,
                                   IntentNodeMapper intentNodeMapper,
                                   IntentTreeCacheManager intentTreeCacheManager,
                                   ObjectMapper objectMapper,
                                   PromptTemplateLoader promptTemplateLoader) {
        this.llmService = llmService;
        this.intentNodeMapper = intentNodeMapper;
        this.intentTreeCacheManager = intentTreeCacheManager;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    @Override
    public List<NodeScore> classifyTargets(String question) {
        IntentTreeData data = loadIntentTreeData();
        if (CollUtil.isEmpty(data.leafNodes()) || StrUtil.isBlank(question)) {
            return List.of();
        }

        try {
            String prompt = buildPrompt(data.leafNodes());
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
            List<NodeScore> parsed = parseScores(raw, data.leafNodes());
            if (CollUtil.isNotEmpty(parsed)) {
                return capAndSort(parsed);
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    @Override
    public IntentNode getNodeById(String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        RagIntentNode node = loadIntentTreeData().id2Node().get(id);
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
                RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH,
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

    private IntentTreeData loadIntentTreeData() {
        List<RagIntentNode> roots = intentTreeCacheManager.getIntentTreeFromCache();
        if (CollUtil.isEmpty(roots)) {
            roots = loadIntentTreeFromDB();
            if (CollUtil.isNotEmpty(roots)) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
            }
        }
        if (CollUtil.isEmpty(roots)) {
            return new IntentTreeData(List.of(), List.of(), Map.of());
        }

        List<RagIntentNode> allNodes = flatten(roots);
        List<RagIntentNode> leafNodes = allNodes.stream()
                .filter(RagIntentNode::isLeaf)
                .toList();
        Map<String, RagIntentNode> id2Node = allNodes.stream()
                .filter(node -> StrUtil.isNotBlank(node.intentCode))
                .collect(java.util.stream.Collectors.toMap(node -> node.intentCode, node -> node, (left, right) -> left, LinkedHashMap::new));
        return new IntentTreeData(allNodes, leafNodes, id2Node);
    }

    private List<RagIntentNode> loadIntentTreeFromDB() {
        List<IntentNodeEntity> entities = intentNodeMapper.selectList(
                new QueryWrapper<IntentNodeEntity>()
                        .eq("deleted", 0)
                        .eq("enabled", 1)
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        );
        if (CollUtil.isEmpty(entities)) {
            return List.of();
        }

        Map<String, RagIntentNode> id2Node = new LinkedHashMap<>();
        for (IntentNodeEntity entity : entities) {
            RagIntentNode node = toNode(entity);
            node.setChildren(new ArrayList<>());
            id2Node.put(node.getId(), node);
        }

        List<RagIntentNode> roots = new ArrayList<>();
        for (RagIntentNode node : id2Node.values()) {
            if (StrUtil.isBlank(node.parentCode) || !id2Node.containsKey(node.parentCode)) {
                roots.add(node);
                continue;
            }
            RagIntentNode parent = id2Node.get(node.parentCode);
            parent.getChildren().add(node);
        }

        fillFullPath(roots, null);
        return roots;
    }

    private List<RagIntentNode> flatten(List<RagIntentNode> roots) {
        List<RagIntentNode> result = new ArrayList<>();
        if (CollUtil.isEmpty(roots)) {
            return result;
        }
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode current = stack.pop();
            if (current instanceof RagIntentNode node) {
                result.add(node);
            }
            if (CollUtil.isNotEmpty(current.getChildren())) {
                List<IntentNode> children = current.getChildren();
                for (int index = children.size() - 1; index >= 0; index--) {
                    stack.push(children.get(index));
                }
            }
        }
        return result;
    }

    private void fillFullPath(List<RagIntentNode> nodes, RagIntentNode parent) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        for (RagIntentNode node : nodes) {
            node.fullPath = parent == null ? node.name : parent.fullPath + " > " + node.name;
            fillFullPath(castNodes(node.getChildren()), node);
        }
    }

    @SuppressWarnings("unchecked")
    private List<RagIntentNode> castNodes(List<IntentNode> nodes) {
        if (CollUtil.isEmpty(nodes)) {
            return List.of();
        }
        return (List<RagIntentNode>) (List<?>) nodes;
    }

    private RagIntentNode toNode(IntentNodeEntity entity) {
        RagIntentNode node = new RagIntentNode();
        node.id = entity.id;
        node.intentCode = entity.intentCode;
        node.name = entity.name;
        node.level = entity.level;
        node.parentCode = entity.parentCode;
        node.description = entity.description;
        node.examples = entity.examples;
        node.collectionName = entity.collectionName;
        node.topK = entity.topK;
        node.mcpToolId = entity.mcpToolId;
        node.kind = entity.kind;
        node.promptSnippet = entity.promptSnippet;
        node.promptTemplate = entity.promptTemplate;
        node.paramPromptTemplate = entity.paramPromptTemplate;
        node.sortOrder = entity.sortOrder;
        node.kbId = entity.kbId;
        return node;
    }

    private record IntentTreeData(List<RagIntentNode> allNodes,
                                  List<RagIntentNode> leafNodes,
                                  Map<String, RagIntentNode> id2Node) {
    }
}
