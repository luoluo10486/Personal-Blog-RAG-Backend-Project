package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.util.LLMResponseCleaner;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.rag.dao.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.rag.enums.IntentKind;
import com.personalblog.ragbackend.rag.enums.IntentLevel;
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
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentTreeCacheManager intentTreeCacheManager;
    private final ObjectMapper objectMapper;

    public DefaultIntentClassifier(LLMService llmService,
                                   IntentNodeMapper intentNodeMapper,
                                   PromptTemplateLoader promptTemplateLoader,
                                   IntentTreeCacheManager intentTreeCacheManager,
                                   ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.intentNodeMapper = intentNodeMapper;
        this.promptTemplateLoader = promptTemplateLoader;
        this.intentTreeCacheManager = intentTreeCacheManager;
        this.objectMapper = objectMapper;
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
        return loadIntentTreeData().id2Node().get(id);
    }

    private List<NodeScore> parseScores(String raw, List<IntentNode> leafNodes) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String normalized = LLMResponseCleaner.stripMarkdownCodeFence(raw);
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
            IntentNode node = leafNodes.stream()
                    .filter(each -> id.equals(each.getIntentCode()))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                continue;
            }
            scores.add(NodeScore.builder()
                    .node(node)
                    .score(score)
                    .build());
        }
        return scores;
    }

    private List<NodeScore> capAndSort(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .limit(MAX_INTENT_COUNT)
                .toList();
    }

    private String buildPrompt(List<IntentNode> leafNodes) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode node : leafNodes) {
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

    private IntentTreeData loadIntentTreeData() {
        List<IntentNode> roots = intentTreeCacheManager.getIntentTreeFromCache();
        if (CollUtil.isEmpty(roots)) {
            roots = loadIntentTreeFromDB();
            if (CollUtil.isNotEmpty(roots)) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
            }
        }
        if (CollUtil.isEmpty(roots)) {
            return new IntentTreeData(List.of(), List.of(), Map.of());
        }

        List<IntentNode> allNodes = flatten(roots);
        List<IntentNode> leafNodes = allNodes.stream()
                .filter(IntentNode::isLeaf)
                .toList();
        Map<String, IntentNode> id2Node = allNodes.stream()
                .filter(node -> StrUtil.isNotBlank(node.getIntentCode()))
                .collect(java.util.stream.Collectors.toMap(IntentNode::getIntentCode, node -> node, (left, right) -> left, LinkedHashMap::new));
        return new IntentTreeData(allNodes, leafNodes, id2Node);
    }

    private List<IntentNode> loadIntentTreeFromDB() {
        List<IntentNodeEntity> entities = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeEntity.class)
                        .eq(IntentNodeEntity::getDeleted, 0)
                        .eq(IntentNodeEntity::getEnabled, 1)
                        .orderByAsc(IntentNodeEntity::getSortOrder)
                        .orderByAsc(IntentNodeEntity::getId)
        );
        if (CollUtil.isEmpty(entities)) {
            return List.of();
        }

        Map<String, IntentNode> id2Node = new LinkedHashMap<>();
        for (IntentNodeEntity entity : entities) {
            IntentNode node = BeanUtil.toBean(entity, IntentNode.class);
            node.setId(entity.getIntentCode());
            node.setParentId(entity.getParentCode());
            node.setCollectionName(entity.getCollectionName());
            node.setTopK(entity.getTopK());
            node.setMcpToolId(entity.getMcpToolId());
            node.setKindCode(entity.getKind());
            node.setParamPromptTemplate(entity.getParamPromptTemplate());
            node.setPromptTemplate(entity.getPromptTemplate());
            node.setPromptSnippet(entity.getPromptSnippet());
            node.setSortOrder(entity.getSortOrder());
            node.setKbId(entity.getKbId());
            node.setChildren(new ArrayList<>());
            if (entity.getLevel() != null) {
                node.setLevel(IntentLevel.fromCode(entity.getLevel()));
            }
            if (entity.getKind() != null) {
                node.setKind(IntentKind.fromCode(entity.getKind()));
            }
            id2Node.put(node.getId(), node);
        }

        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : id2Node.values()) {
            if (StrUtil.isBlank(node.getParentId()) || !id2Node.containsKey(node.getParentId())) {
                roots.add(node);
                continue;
            }
            IntentNode parent = id2Node.get(node.getParentId());
            parent.getChildren().add(node);
        }

        fillFullPath(roots, null);
        return roots;
    }

    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        if (CollUtil.isEmpty(roots)) {
            return result;
        }
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode current = stack.pop();
            result.add(current);
            if (CollUtil.isNotEmpty(current.getChildren())) {
                List<IntentNode> children = current.getChildren();
                for (int index = children.size() - 1; index >= 0; index--) {
                    stack.push(children.get(index));
                }
            }
        }
        return result;
    }

    private void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        for (IntentNode node : nodes) {
            node.setFullPath(parent == null ? node.getName() : parent.getFullPath() + " > " + node.getName());
            fillFullPath(node.getChildren(), node);
        }
    }

    private record IntentTreeData(List<IntentNode> allNodes,
                                  List<IntentNode> leafNodes,
                                  Map<String, IntentNode> id2Node) {
    }
}
