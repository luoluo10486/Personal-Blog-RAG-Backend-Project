package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.personalblog.ragbackend.rag.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.rag.dao.mapper.IntentNodeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagIntentCatalogService implements IntentNodeRegistry {
    private final IntentNodeMapper intentNodeMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    public RagIntentCatalogService(IntentNodeMapper intentNodeMapper,
                                   IntentTreeCacheManager intentTreeCacheManager) {
        this.intentNodeMapper = intentNodeMapper;
        this.intentTreeCacheManager = intentTreeCacheManager;
    }

    public List<RagIntentNode> listLeafNodes() {
        return loadIntentTreeData().leafNodes();
    }

    public RagIntentNode findByIntentCode(String intentCode) {
        if (StrUtil.isBlank(intentCode)) {
            return null;
        }
        return loadIntentTreeData().id2Node().get(intentCode);
    }

    @Override
    public IntentNode getNodeById(String id) {
        RagIntentNode node = findByIntentCode(id);
        if (node == null) {
            return null;
        }
        IntentNode target = new IntentNode();
        target.setId(node.intentCode);
        target.setName(node.name);
        target.setLevel(node.getLevel());
        target.setParentId(node.parentCode);
        target.setDescription(node.description);
        target.setExamples(node.getExamples());
        target.setCollectionName(node.collectionName);
        target.setTopK(node.topK);
        target.setMcpToolId(node.mcpToolId);
        target.setKind(node.getKind());
        target.setPromptSnippet(node.promptSnippet);
        target.setPromptTemplate(node.promptTemplate);
        target.setParamPromptTemplate(node.paramPromptTemplate);
        target.setSortOrder(node.sortOrder);
        target.setFullPath(node.fullPath);
        target.setKbId(node.kbId);
        target.setChildren(node.getChildren());
        return target;
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        List<NodeScore> mcpIntents = new ArrayList<>();
        List<NodeScore> kbIntents = new ArrayList<>();
        if (CollUtil.isEmpty(subIntents)) {
            return new IntentGroup(List.of(), List.of());
        }
        for (SubQuestionIntent subQuestionIntent : subIntents) {
            if (subQuestionIntent == null || CollUtil.isEmpty(subQuestionIntent.nodeScores())) {
                continue;
            }
            for (NodeScore nodeScore : subQuestionIntent.nodeScores()) {
                if (nodeScore == null || nodeScore.node() == null) {
                    continue;
                }
                if (nodeScore.node().isMcp()) {
                    mcpIntents.add(nodeScore);
                } else {
                    kbIntents.add(nodeScore);
                }
            }
        }
        return new IntentGroup(mcpIntents, kbIntents);
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return CollUtil.isNotEmpty(nodeScores)
                && nodeScores.stream().allMatch(score -> score != null && score.node() != null && score.node().isSystem());
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
                .collect(Collectors.toMap(node -> node.intentCode, node -> node, (left, right) -> left, LinkedHashMap::new));
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

        sortTree(roots);
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

    private void sortTree(List<RagIntentNode> nodes) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        nodes.sort(Comparator.comparing((RagIntentNode node) -> node.sortOrder == null ? 0 : node.sortOrder)
                .thenComparing(node -> node.intentCode == null ? "" : node.intentCode));
        for (RagIntentNode node : nodes) {
            if (CollUtil.isNotEmpty(node.getChildren())) {
                sortTree(castNodes(node.getChildren()));
            }
        }
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

    @SuppressWarnings("unchecked")
    private List<RagIntentNode> castNodes(List<IntentNode> nodes) {
        if (CollUtil.isEmpty(nodes)) {
            return List.of();
        }
        return (List<RagIntentNode>) (List<?>) nodes;
    }

    private record IntentTreeData(List<RagIntentNode> allNodes,
                                  List<RagIntentNode> leafNodes,
                                  Map<String, RagIntentNode> id2Node) {
    }
}

