package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IntentTreeAdminService {
    private final IntentNodeMapper intentNodeMapper;

    public IntentTreeAdminService(IntentNodeMapper intentNodeMapper) {
        this.intentNodeMapper = intentNodeMapper;
    }

    public List<IntentNodeEntity> tree() {
        return intentNodeMapper.selectList(new QueryWrapper<IntentNodeEntity>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public Long create(IntentNodeEntity request) {
        if (request.intentCode == null || request.intentCode.isBlank() || request.name == null || request.name.isBlank()) {
            throw new IllegalArgumentException("意图编码和名称不能为空");
        }
        request.createdAt = LocalDateTime.now();
        request.updatedAt = LocalDateTime.now();
        request.enabled = request.enabled == null ? 1 : request.enabled;
        request.deleted = 0;
        intentNodeMapper.insert(request);
        return request.id;
    }

    public void update(Long id, IntentNodeEntity request) {
        IntentNodeEntity entity = require(id);
        if (request.intentCode != null) entity.intentCode = request.intentCode;
        if (request.name != null) entity.name = request.name;
        if (request.level != null) entity.level = request.level;
        if (request.parentCode != null) entity.parentCode = request.parentCode;
        if (request.description != null) entity.description = request.description;
        if (request.examples != null) entity.examples = request.examples;
        if (request.collectionName != null) entity.collectionName = request.collectionName;
        if (request.topK != null) entity.topK = request.topK;
        if (request.mcpToolId != null) entity.mcpToolId = request.mcpToolId;
        if (request.kind != null) entity.kind = request.kind;
        if (request.promptSnippet != null) entity.promptSnippet = request.promptSnippet;
        if (request.promptTemplate != null) entity.promptTemplate = request.promptTemplate;
        if (request.paramPromptTemplate != null) entity.paramPromptTemplate = request.paramPromptTemplate;
        if (request.sortOrder != null) entity.sortOrder = request.sortOrder;
        if (request.enabled != null) entity.enabled = request.enabled;
        entity.updatedAt = LocalDateTime.now();
        intentNodeMapper.updateById(entity);
    }

    public void delete(Long id) {
        intentNodeMapper.deleteById(id);
    }

    public void batchEnable(List<Long> ids) {
        batchToggle(ids, 1);
    }

    public void batchDisable(List<Long> ids) {
        batchToggle(ids, 0);
    }

    public void batchDelete(List<Long> ids) {
        if (ids == null) return;
        ids.forEach(intentNodeMapper::deleteById);
    }

    private void batchToggle(List<Long> ids, int enabled) {
        if (ids == null) return;
        for (Long id : ids) {
            IntentNodeEntity entity = require(id);
            entity.enabled = enabled;
            entity.updatedAt = LocalDateTime.now();
            intentNodeMapper.updateById(entity);
        }
    }

    private IntentNodeEntity require(Long id) {
        IntentNodeEntity entity = intentNodeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("意图节点不存在");
        }
        return entity;
    }
}
