package com.personalblog.ragbackend.ingestion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.personalblog.ragbackend.ingestion.service.IntentTreeService;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.IntentNodeTreeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentTreeServiceImpl extends ServiceImpl<IntentNodeMapper, IntentNodeEntity> implements IntentTreeService {

    @Override
    public List<IntentNodeTreeVO> getFullTree() {
        List<IntentNodeEntity> list = this.list(new QueryWrapper<IntentNodeEntity>()
                .eq("deleted", 0)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        Map<String, List<IntentNodeEntity>> parentMap = new LinkedHashMap<>();
        for (IntentNodeEntity node : list) {
            String parent = StringUtils.hasText(node.parentCode) ? node.parentCode : "ROOT";
            parentMap.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
        }
        List<IntentNodeTreeVO> tree = new ArrayList<>();
        for (IntentNodeEntity root : parentMap.getOrDefault("ROOT", Collections.emptyList())) {
            tree.add(buildTree(root, parentMap));
        }
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNode(IntentNodeCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new IllegalArgumentException("请求不能为空"));
        IntentNodeEntity entity = new IntentNodeEntity();
        entity.kbId = parseNullableLong(requestParam.getKbId());
        entity.intentCode = requestParam.getIntentCode();
        entity.name = requestParam.getName();
        entity.level = requestParam.getLevel();
        entity.parentCode = requestParam.getParentCode();
        entity.description = requestParam.getDescription();
        entity.examples = requestParam.getExamples() == null ? null : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(requestParam.getExamples()).toString();
        entity.collectionName = null;
        entity.topK = requestParam.getTopK();
        entity.mcpToolId = requestParam.getMcpToolId();
        entity.kind = requestParam.getKind();
        entity.sortOrder = requestParam.getSortOrder();
        entity.enabled = requestParam.getEnabled() == null ? 1 : requestParam.getEnabled();
        entity.promptSnippet = requestParam.getPromptSnippet();
        entity.promptTemplate = requestParam.getPromptTemplate();
        entity.paramPromptTemplate = requestParam.getParamPromptTemplate();
        entity.deleted = 0;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        this.save(entity);
        return String.valueOf(entity.id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(String id, IntentNodeUpdateRequest requestParam) {
        IntentNodeEntity entity = require(id);
        if (requestParam.getName() != null) {
            entity.name = requestParam.getName();
        }
        if (requestParam.getLevel() != null) {
            entity.level = requestParam.getLevel();
        }
        if (requestParam.getParentCode() != null) {
            entity.parentCode = requestParam.getParentCode();
        }
        if (requestParam.getDescription() != null) {
            entity.description = requestParam.getDescription();
        }
        if (requestParam.getExamples() != null) {
            entity.examples = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(requestParam.getExamples()).toString();
        }
        if (requestParam.getCollectionName() != null) {
            entity.collectionName = requestParam.getCollectionName();
        }
        if (requestParam.getTopK() != null) {
            entity.topK = requestParam.getTopK();
        }
        if (requestParam.getKind() != null) {
            entity.kind = requestParam.getKind();
        }
        if (requestParam.getSortOrder() != null) {
            entity.sortOrder = requestParam.getSortOrder();
        }
        if (requestParam.getEnabled() != null) {
            entity.enabled = requestParam.getEnabled();
        }
        if (requestParam.getPromptSnippet() != null) {
            entity.promptSnippet = requestParam.getPromptSnippet();
        }
        if (requestParam.getPromptTemplate() != null) {
            entity.promptTemplate = requestParam.getPromptTemplate();
        }
        if (requestParam.getParamPromptTemplate() != null) {
            entity.paramPromptTemplate = requestParam.getParamPromptTemplate();
        }
        entity.updatedAt = LocalDateTime.now();
        this.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(String id) {
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableNodes(List<String> ids) {
        updateEnabled(ids, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableNodes(List<String> ids) {
        updateEnabled(ids, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteNodes(List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            this.removeById(id);
        }
    }

    private void updateEnabled(List<String> ids, int enabled) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            IntentNodeEntity entity = require(id);
            entity.enabled = enabled;
            entity.updatedAt = LocalDateTime.now();
            this.updateById(entity);
        }
    }

    private IntentNodeTreeVO buildTree(IntentNodeEntity current,
                                       Map<String, List<IntentNodeEntity>> parentMap) {
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

    private IntentNodeEntity require(String id) {
        Assert.notNull(id, () -> new IllegalArgumentException("Intent node does not exist"));
        IntentNodeEntity entity = this.getById(id);
        Assert.notNull(entity, () -> new IllegalArgumentException("Intent node does not exist"));
        return entity;
    }

    private Long parseNullableLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.valueOf(value.trim());
    }
}
