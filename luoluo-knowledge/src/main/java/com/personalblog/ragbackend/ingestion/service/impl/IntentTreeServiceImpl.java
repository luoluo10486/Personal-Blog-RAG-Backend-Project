package com.personalblog.ragbackend.ingestion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.ingestion.service.IntentTreeService;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.IntentNodeTreeVO;
import com.personalblog.ragbackend.rag.core.intent.IntentTreeCacheManager;
import com.personalblog.ragbackend.rag.core.intent.IntentTreeFactory;
import com.personalblog.ragbackend.rag.enums.IntentKind;
import com.personalblog.ragbackend.rag.enums.IntentLevel;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IntentTreeServiceImpl extends ServiceImpl<IntentNodeMapper, IntentNodeEntity> implements IntentTreeService {
    private final IntentTreeCacheManager intentTreeCacheManager;
    private static final Gson GSON = new Gson();

    public IntentTreeServiceImpl(IntentTreeCacheManager intentTreeCacheManager) {
        this.intentTreeCacheManager = intentTreeCacheManager;
    }

    @Override
    public List<IntentNodeTreeVO> getFullTree() {
        List<IntentNodeEntity> list = this.list(new LambdaQueryWrapper<IntentNodeEntity>()
                .eq(IntentNodeEntity::getDeleted, 0)
                .orderByAsc(IntentNodeEntity::getSortOrder, IntentNodeEntity::getId));

        Map<String, List<IntentNodeEntity>> parentMap = list.stream()
                .collect(Collectors.groupingBy(node -> {
                    String parent = node.parentCode;
                    return parent == null ? "ROOT" : parent;
                }));

        List<IntentNodeEntity> roots = parentMap.getOrDefault("ROOT", Collections.emptyList());
        List<IntentNodeTreeVO> tree = new ArrayList<>();
        for (IntentNodeEntity root : roots) {
            tree.add(buildTree(root, parentMap));
        }
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNode(IntentNodeCreateRequest requestParam) {
        long count = this.count(new LambdaQueryWrapper<IntentNodeEntity>()
                .eq(IntentNodeEntity::getIntentCode, requestParam.getIntentCode())
                .eq(IntentNodeEntity::getDeleted, 0));
        if (count > 0) {
            throw new IllegalArgumentException("Intent code already exists: " + requestParam.getIntentCode());
        }

        if (Objects.equals(requestParam.getLevel(), IntentLevel.TOPIC.getCode())
                && Objects.equals(requestParam.getKind(), IntentKind.KB.getCode())
                && StrUtil.isBlank(requestParam.getKbId())) {
            throw new IllegalArgumentException("TOPIC level KB intent must provide kbId");
        }

        IntentNodeEntity node = new IntentNodeEntity();
        node.intentCode = requestParam.getIntentCode();
        node.kbId = StrUtil.isNotBlank(requestParam.getKbId()) ? Long.valueOf(requestParam.getKbId()) : null;
        node.collectionName = null;
        node.name = requestParam.getName();
        node.level = requestParam.getLevel();
        node.parentCode = requestParam.getParentCode();
        node.description = requestParam.getDescription();
        node.mcpToolId = requestParam.getMcpToolId();
        node.examples = requestParam.getExamples() == null ? null : GSON.toJson(requestParam.getExamples());
        node.topK = normalizeTopK(requestParam.getTopK());
        node.kind = requestParam.getKind() == null ? 0 : requestParam.getKind();
        node.sortOrder = requestParam.getSortOrder() == null ? 0 : requestParam.getSortOrder();
        node.enabled = requestParam.getEnabled() == null ? 1 : requestParam.getEnabled();
        node.paramPromptTemplate = requestParam.getParamPromptTemplate();
        node.promptSnippet = requestParam.getPromptSnippet();
        node.promptTemplate = requestParam.getPromptTemplate();
        node.deleted = 0;
        node.createdAt = java.time.LocalDateTime.now();
        node.updatedAt = java.time.LocalDateTime.now();

        this.save(node);
        intentTreeCacheManager.clearIntentTreeCache();
        return String.valueOf(node.id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(String id, IntentNodeUpdateRequest req) {
        IntentNodeEntity node = this.getById(id);
        if (node == null || Objects.equals(node.deleted, 1)) {
            throw new IllegalArgumentException("Intent node not found: " + id);
        }
        if (req.getName() != null) {
            node.name = req.getName();
        }
        if (req.getLevel() != null) {
            node.level = req.getLevel();
        }
        if (req.getParentCode() != null) {
            node.parentCode = req.getParentCode();
        }
        if (req.getDescription() != null) {
            node.description = req.getDescription();
        }
        if (req.getExamples() != null) {
            node.examples = GSON.toJson(req.getExamples());
        }
        if (req.getCollectionName() != null) {
            node.collectionName = req.getCollectionName();
        }
        if (req.getTopK() != null) {
            node.topK = normalizeTopK(req.getTopK());
        }
        if (req.getKind() != null) {
            node.kind = req.getKind();
        }
        if (req.getSortOrder() != null) {
            node.sortOrder = req.getSortOrder();
        }
        if (req.getEnabled() != null) {
            node.enabled = req.getEnabled();
        }
        if (req.getPromptSnippet() != null) {
            node.promptSnippet = req.getPromptSnippet();
        }
        if (req.getPromptTemplate() != null) {
            node.promptTemplate = req.getPromptTemplate();
        }
        if (req.getParamPromptTemplate() != null) {
            node.paramPromptTemplate = req.getParamPromptTemplate();
        }
        node.updatedAt = java.time.LocalDateTime.now();
        this.updateById(node);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(String id) {
        this.removeById(id);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableNodes(List<String> ids) {
        List<IntentNodeEntity> targetNodes = listAndValidateTargetNodes(ids);
        String operator = UserContext.getUsername();
        targetNodes.forEach(node -> node.enabled = 1);
        this.updateBatchById(targetNodes);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableNodes(List<String> ids) {
        List<IntentNodeEntity> targetNodes = listAndValidateTargetNodes(ids);
        List<IntentNodeEntity> allActiveNodes = listActiveNodes();
        Map<String, List<IntentNodeEntity>> childrenMap = buildChildrenMap(allActiveNodes);
        Set<String> targetIdSet = targetNodes.stream().map(node -> String.valueOf(node.id)).collect(Collectors.toSet());
        for (IntentNodeEntity targetNode : targetNodes) {
            List<IntentNodeEntity> descendants = collectDescendants(targetNode.intentCode, childrenMap);
            List<IntentNodeEntity> enabledButNotSelected = descendants.stream()
                    .filter(item -> Objects.equals(item.enabled, 1) && !targetIdSet.contains(String.valueOf(item.id)))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(enabledButNotSelected)) {
                throw new IllegalArgumentException("Cannot disable node with enabled descendants: " + targetNode.name);
            }
        }
        String operator = UserContext.getUsername();
        targetNodes.forEach(node -> node.enabled = 0);
        this.updateBatchById(targetNodes);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteNodes(List<String> ids) {
        List<IntentNodeEntity> targetNodes = listAndValidateTargetNodes(ids);
        List<IntentNodeEntity> allActiveNodes = listActiveNodes();
        Map<String, List<IntentNodeEntity>> childrenMap = buildChildrenMap(allActiveNodes);
        Set<String> targetIdSet = targetNodes.stream().map(node -> String.valueOf(node.id)).collect(Collectors.toSet());
        for (IntentNodeEntity targetNode : targetNodes) {
            List<IntentNodeEntity> descendants = collectDescendants(targetNode.intentCode, childrenMap);
            List<IntentNodeEntity> notSelectedDescendants = descendants.stream()
                    .filter(item -> !targetIdSet.contains(String.valueOf(item.id)))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(notSelectedDescendants)) {
                List<IntentNodeEntity> enabledDescendants = notSelectedDescendants.stream()
                        .filter(item -> Objects.equals(item.enabled, 1))
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(enabledDescendants)) {
                    throw new IllegalArgumentException("Cannot delete node with enabled descendants: " + targetNode.name);
                }
                throw new IllegalArgumentException("Cannot delete node with unselected descendants: " + targetNode.name);
            }
        }
        this.removeByIds(targetIdSet);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    public int initFromFactory() {
        List<com.personalblog.ragbackend.rag.core.intent.IntentNode> roots = IntentTreeFactory.buildIntentTree();
        List<com.personalblog.ragbackend.rag.core.intent.IntentNode> allNodes = flatten(roots);
        int sort = 0;
        int created = 0;
        for (com.personalblog.ragbackend.rag.core.intent.IntentNode node : allNodes) {
            if (existsByIntentCode(node.getId())) {
                continue;
            }
            IntentNodeCreateRequest nodeCreateRequest = IntentNodeCreateRequest.builder()
                    .kbId(node.getKbId())
                    .intentCode(node.getId())
                    .name(node.getName())
                    .level(mapLevel(node.getLevel()))
                    .parentCode(node.getParentId())
                    .description(node.getDescription())
                    .examples(node.getExamples())
                    .topK(normalizeTopK(node.getTopK()))
                    .kind(mapKind(node.getKind()))
                    .mcpToolId(node.getMcpToolId())
                    .sortOrder(sort++)
                    .enabled(1)
                    .promptTemplate(node.getPromptTemplate())
                    .promptSnippet(node.getPromptSnippet())
                    .paramPromptTemplate(node.getParamPromptTemplate())
                    .build();
            createNode(nodeCreateRequest);
            created++;
        }
        return created;
    }

    private List<com.personalblog.ragbackend.rag.core.intent.IntentNode> flatten(List<com.personalblog.ragbackend.rag.core.intent.IntentNode> roots) {
        List<com.personalblog.ragbackend.rag.core.intent.IntentNode> result = new ArrayList<>();
        Deque<com.personalblog.ragbackend.rag.core.intent.IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            com.personalblog.ragbackend.rag.core.intent.IntentNode n = stack.pop();
            result.add(n);
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                List<com.personalblog.ragbackend.rag.core.intent.IntentNode> children = n.getChildren();
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return result;
    }

    private IntentNodeTreeVO buildTree(IntentNodeEntity current, Map<String, List<IntentNodeEntity>> parentMap) {
        IntentNodeTreeVO result = BeanUtil.toBean(current, IntentNodeTreeVO.class);
        result.setId(current.id == null ? null : String.valueOf(current.id));
        List<IntentNodeEntity> children = parentMap.getOrDefault(current.intentCode, Collections.emptyList());
        if (!children.isEmpty()) {
            List<IntentNodeTreeVO> childVOs = new ArrayList<>();
            for (IntentNodeEntity child : children) {
                childVOs.add(buildTree(child, parentMap));
            }
            result.setChildren(childVOs);
        }
        return result;
    }

    private List<IntentNodeEntity> listAndValidateTargetNodes(List<String> ids) {
        Assert.notEmpty(ids, () -> new IllegalArgumentException("Please select at least one node"));
        List<String> normalizedIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Assert.notEmpty(normalizedIds, () -> new IllegalArgumentException("Node ids cannot be empty"));
        List<IntentNodeEntity> targetNodes = this.list(new LambdaQueryWrapper<IntentNodeEntity>()
                .in(IntentNodeEntity::getId, normalizedIds)
                .eq(IntentNodeEntity::getDeleted, 0));
        if (targetNodes.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("Some nodes do not exist or were deleted");
        }
        return targetNodes;
    }

    private List<IntentNodeEntity> listActiveNodes() {
        return this.list(new LambdaQueryWrapper<IntentNodeEntity>()
                .eq(IntentNodeEntity::getDeleted, 0));
    }

    private Map<String, List<IntentNodeEntity>> buildChildrenMap(List<IntentNodeEntity> nodes) {
        return nodes.stream().collect(Collectors.groupingBy(node -> {
            String parentCode = node.parentCode;
            return parentCode == null ? "ROOT" : parentCode;
        }));
    }

    private List<IntentNodeEntity> collectDescendants(String intentCode, Map<String, List<IntentNodeEntity>> childrenMap) {
        if (StrUtil.isBlank(intentCode)) {
            return Collections.emptyList();
        }
        List<IntentNodeEntity> result = new ArrayList<>();
        Deque<IntentNodeEntity> stack = new ArrayDeque<>(childrenMap.getOrDefault(intentCode, Collections.emptyList()));
        while (!stack.isEmpty()) {
            IntentNodeEntity current = stack.pop();
            result.add(current);
            List<IntentNodeEntity> children = childrenMap.getOrDefault(current.intentCode, Collections.emptyList());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return result;
    }

    private boolean existsByIntentCode(String intentCode) {
        return baseMapper.selectCount(new LambdaQueryWrapper<IntentNodeEntity>()
                .eq(IntentNodeEntity::getIntentCode, intentCode)
                .eq(IntentNodeEntity::getDeleted, 0)) > 0;
    }

    private Integer normalizeTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("TopK must be greater than 0");
        }
        return topK;
    }

    private int mapLevel(IntentLevel level) {
        return level.getCode();
    }

    private int mapKind(IntentKind kind) {
        return kind == null ? 0 : kind.getCode();
    }
}
