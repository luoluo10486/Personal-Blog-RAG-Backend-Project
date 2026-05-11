package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.rag.core.mcp.McpToolClient;
import com.personalblog.ragbackend.rag.core.mcp.McpToolDescriptor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IntentTreeAdminService {
    private final IntentNodeMapper intentNodeMapper;
    private final McpToolClient mcpToolClient;

    public IntentTreeAdminService(IntentNodeMapper intentNodeMapper,
                                  McpToolClient mcpToolClient) {
        this.intentNodeMapper = intentNodeMapper;
        this.mcpToolClient = mcpToolClient;
    }

    public List<IntentNodeEntity> tree() {
        return intentNodeMapper.selectList(new QueryWrapper<IntentNodeEntity>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public List<McpToolDescriptor> listMcpTools() {
        return mcpToolClient.listTools();
    }

    public Long create(IntentNodeEntity request) {
        validate(request, true);
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
        validate(entity, false);
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
        if (ids == null) {
            return;
        }
        ids.forEach(intentNodeMapper::deleteById);
    }

    private void batchToggle(List<Long> ids, int enabled) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            IntentNodeEntity entity = require(id);
            entity.enabled = enabled;
            validate(entity, false);
            entity.updatedAt = LocalDateTime.now();
            intentNodeMapper.updateById(entity);
        }
    }

    private IntentNodeEntity require(Long id) {
        IntentNodeEntity entity = intentNodeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Intent node does not exist");
        }
        return entity;
    }

    private void validate(IntentNodeEntity entity, boolean creating) {
        if (entity == null) {
            throw new IllegalArgumentException("Intent node is required");
        }
        trim(entity);
        if (creating && (entity.intentCode == null || entity.name == null)) {
            throw new IllegalArgumentException("Intent code and name are required");
        }
        int kind = entity.kind == null ? 0 : entity.kind;
        if (kind == 2) {
            if (entity.mcpToolId == null) {
                throw new IllegalArgumentException("MCP intent must provide mcpToolId");
            }
            entity.collectionName = null;
        } else if (kind == 1) {
            entity.collectionName = null;
            entity.mcpToolId = null;
            entity.paramPromptTemplate = null;
        } else {
            entity.mcpToolId = null;
            entity.paramPromptTemplate = null;
        }
    }

    private void trim(IntentNodeEntity entity) {
        entity.intentCode = trimToNull(entity.intentCode);
        entity.name = trimToNull(entity.name);
        entity.parentCode = trimToNull(entity.parentCode);
        entity.description = trimToNull(entity.description);
        entity.examples = trimToNull(entity.examples);
        entity.collectionName = trimToNull(entity.collectionName);
        entity.mcpToolId = trimToNull(entity.mcpToolId);
        entity.promptSnippet = trimToNull(entity.promptSnippet);
        entity.promptTemplate = trimToNull(entity.promptTemplate);
        entity.paramPromptTemplate = trimToNull(entity.paramPromptTemplate);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
