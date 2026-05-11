package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagIntentCatalogService {
    private final IntentNodeMapper intentNodeMapper;

    public RagIntentCatalogService(IntentNodeMapper intentNodeMapper) {
        this.intentNodeMapper = intentNodeMapper;
    }

    public List<RagIntentNode> listLeafNodes() {
        List<RagIntentNode> allNodes = listAllNodes();
        if (CollUtil.isEmpty(allNodes)) {
            return List.of();
        }
        Map<String, RagIntentNode> byCode = allNodes.stream()
                .filter(node -> StrUtil.isNotBlank(node.intentCode))
                .collect(java.util.stream.Collectors.toMap(node -> node.intentCode, node -> node, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<RagIntentNode>> childrenByParent = new HashMap<>();
        for (RagIntentNode node : byCode.values()) {
            if (StrUtil.isBlank(node.parentCode)) {
                continue;
            }
            childrenByParent.computeIfAbsent(node.parentCode, key -> new ArrayList<>()).add(node);
        }
        for (List<RagIntentNode> children : childrenByParent.values()) {
            children.sort(Comparator.comparing((RagIntentNode n) -> n.sortOrder == null ? 0 : n.sortOrder)
                    .thenComparing(n -> n.id == null ? Long.MAX_VALUE : n.id));
        }
        List<RagIntentNode> roots = byCode.values().stream()
                .filter(node -> StrUtil.isBlank(node.parentCode) || !byCode.containsKey(node.parentCode))
                .sorted(Comparator.comparing((RagIntentNode n) -> n.sortOrder == null ? 0 : n.sortOrder)
                        .thenComparing(n -> n.id == null ? Long.MAX_VALUE : n.id))
                .toList();
        fillFullPath(roots, childrenByParent, null);
        return byCode.values().stream()
                .filter(node -> !childrenByParent.containsKey(node.intentCode))
                .filter(node -> node.kind == null || node.kind != -1)
                .toList();
    }

    public RagIntentNode findByIntentCode(String intentCode) {
        if (StrUtil.isBlank(intentCode)) {
            return null;
        }
        return listAllNodes().stream()
                .filter(node -> intentCode.equals(node.intentCode))
                .findFirst()
                .orElse(null);
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

    private List<RagIntentNode> listAllNodes() {
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
        List<RagIntentNode> nodes = new ArrayList<>();
        for (IntentNodeEntity entity : entities) {
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
            nodes.add(node);
        }
        return nodes;
    }

    private void fillFullPath(List<RagIntentNode> nodes,
                              Map<String, List<RagIntentNode>> childrenByParent,
                              String parentPath) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        for (RagIntentNode node : nodes) {
            node.fullPath = StrUtil.isBlank(parentPath) ? node.name : parentPath + " > " + node.name;
            fillFullPath(childrenByParent.get(node.intentCode), childrenByParent, node.fullPath);
        }
    }
}
