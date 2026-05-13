package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IntentNodeMapper;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.IntentNodeTreeVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentTreeAdminService {
    private final IntentNodeMapper intentNodeMapper;
    private final ObjectMapper objectMapper;

    public IntentTreeAdminService(IntentNodeMapper intentNodeMapper,
                                  ObjectMapper objectMapper) {
        this.intentNodeMapper = intentNodeMapper;
        this.objectMapper = objectMapper;
    }

    public List<IntentNodeTreeVO> tree() {
        List<IntentNodeEntity> list = intentNodeMapper.selectList(new QueryWrapper<IntentNodeEntity>()
                .eq("deleted", 0)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        Map<String, List<IntentNodeEntity>> parentMap = new LinkedHashMap<>();
        for (IntentNodeEntity node : list) {
            String parent = StringUtils.hasText(node.parentCode) ? node.parentCode : "ROOT";
            parentMap.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
        }
        List<IntentNodeEntity> roots = parentMap.getOrDefault("ROOT", Collections.emptyList());
        List<IntentNodeTreeVO> tree = new ArrayList<>();
        for (IntentNodeEntity root : roots) {
            tree.add(buildTree(root, parentMap));
        }
        return tree;
    }

    public String create(IntentNodeCreateRequest request) {
        IntentNodeEntity entity = new IntentNodeEntity();
        entity.kbId = parseNullableLong(request == null ? null : request.getKbId());
        entity.intentCode = request == null ? null : request.getIntentCode();
        entity.name = request == null ? null : request.getName();
        entity.level = request == null ? null : request.getLevel();
        entity.parentCode = request == null ? null : request.getParentCode();
        entity.description = request == null ? null : request.getDescription();
        entity.examples = toExamplesJson(request == null ? null : request.getExamples());
        entity.topK = request == null ? null : request.getTopK();
        entity.mcpToolId = request == null ? null : request.getMcpToolId();
        entity.kind = request == null ? null : request.getKind();
        entity.sortOrder = request == null ? null : request.getSortOrder();
        entity.enabled = request == null ? null : request.getEnabled();
        entity.promptSnippet = request == null ? null : request.getPromptSnippet();
        entity.promptTemplate = request == null ? null : request.getPromptTemplate();
        entity.paramPromptTemplate = request == null ? null : request.getParamPromptTemplate();
        validate(entity, true);
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        entity.enabled = entity.enabled == null ? 1 : entity.enabled;
        entity.deleted = 0;
        intentNodeMapper.insert(entity);
        return entity.id == null ? null : String.valueOf(entity.id);
    }

    public void update(String id, IntentNodeUpdateRequest request) {
        IntentNodeEntity entity = require(id);
        if (request.getName() != null) {
            entity.name = request.getName();
        }
        if (request.getLevel() != null) {
            entity.level = request.getLevel();
        }
        if (request.getParentCode() != null) {
            entity.parentCode = request.getParentCode();
        }
        if (request.getDescription() != null) {
            entity.description = request.getDescription();
        }
        if (request.getExamples() != null) {
            entity.examples = toExamplesJson(request.getExamples());
        }
        if (request.getCollectionName() != null) {
            entity.collectionName = request.getCollectionName();
        }
        if (request.getTopK() != null) {
            entity.topK = request.getTopK();
        }
        if (request.getKind() != null) {
            entity.kind = request.getKind();
        }
        if (request.getSortOrder() != null) {
            entity.sortOrder = request.getSortOrder();
        }
        if (request.getEnabled() != null) {
            entity.enabled = request.getEnabled();
        }
        if (request.getPromptSnippet() != null) {
            entity.promptSnippet = request.getPromptSnippet();
        }
        if (request.getPromptTemplate() != null) {
            entity.promptTemplate = request.getPromptTemplate();
        }
        if (request.getParamPromptTemplate() != null) {
            entity.paramPromptTemplate = request.getParamPromptTemplate();
        }
        validate(entity, false);
        entity.updatedAt = LocalDateTime.now();
        intentNodeMapper.updateById(entity);
    }

    public void delete(String id) {
        intentNodeMapper.deleteById(parseRequiredLong(id, "Intent node does not exist"));
    }

    public void batchEnable(List<String> ids) {
        batchToggle(ids, 1);
    }

    public void batchDisable(List<String> ids) {
        batchToggle(ids, 0);
    }

    public void batchDelete(List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (StringUtils.hasText(id)) {
                intentNodeMapper.deleteById(parseRequiredLong(id, "Intent node does not exist"));
            }
        }
    }

    private void batchToggle(List<String> ids, int enabled) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            IntentNodeEntity entity = require(id);
            entity.enabled = enabled;
            validate(entity, false);
            entity.updatedAt = LocalDateTime.now();
            intentNodeMapper.updateById(entity);
        }
    }

    private IntentNodeTreeVO buildTree(IntentNodeEntity current,
                                       Map<String, List<IntentNodeEntity>> parentMap) {
        IntentNodeTreeVO result = toTreeVO(current);
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

    private IntentNodeTreeVO toTreeVO(IntentNodeEntity entity) {
        IntentNodeTreeVO vo = new IntentNodeTreeVO();
        vo.setId(entity.id == null ? null : String.valueOf(entity.id));
        vo.setIntentCode(entity.intentCode);
        vo.setName(entity.name);
        vo.setLevel(entity.level);
        vo.setParentCode(entity.parentCode);
        vo.setDescription(entity.description);
        vo.setExamples(entity.examples);
        vo.setCollectionName(entity.collectionName);
        vo.setTopK(entity.topK);
        vo.setKind(entity.kind);
        vo.setSortOrder(entity.sortOrder);
        vo.setEnabled(entity.enabled);
        vo.setMcpToolId(entity.mcpToolId);
        vo.setPromptSnippet(entity.promptSnippet);
        vo.setPromptTemplate(entity.promptTemplate);
        vo.setParamPromptTemplate(entity.paramPromptTemplate);
        return vo;
    }

    private IntentNodeEntity require(String id) {
        IntentNodeEntity entity = intentNodeMapper.selectById(parseRequiredLong(id, "Intent node does not exist"));
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
        if (creating && (!StringUtils.hasText(entity.intentCode) || !StringUtils.hasText(entity.name))) {
            throw new IllegalArgumentException("Intent code and name are required");
        }
        int kind = entity.kind == null ? 0 : entity.kind;
        if (kind == 2) {
            if (!StringUtils.hasText(entity.mcpToolId)) {
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

    private String toExamplesJson(List<String> examples) {
        if (examples == null) {
            return null;
        }
        List<String> normalized = examples.stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Intent examples serialization failed", exception);
        }
    }

    @SuppressWarnings("unused")
    private List<String> parseExamples(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of(raw);
        }
    }

    private Long parseNullableLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric id: " + value, exception);
        }
    }

    private Long parseRequiredLong(String value, String message) {
        Long parsed = parseNullableLong(value);
        if (parsed == null) {
            throw new IllegalArgumentException(message);
        }
        return parsed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
