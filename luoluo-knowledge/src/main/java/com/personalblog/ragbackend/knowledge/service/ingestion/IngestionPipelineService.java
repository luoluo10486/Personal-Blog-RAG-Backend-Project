package com.personalblog.ragbackend.knowledge.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionPipelineEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionPipelineNodeEntity;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineNodeRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineNodeView;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionPipelineView;
import com.personalblog.ragbackend.knowledge.mapper.IngestionPipelineMapper;
import com.personalblog.ragbackend.knowledge.mapper.IngestionPipelineNodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionPipelineService {
    private final IngestionPipelineMapper pipelineMapper;
    private final IngestionPipelineNodeMapper nodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;

    public IngestionPipelineService(IngestionPipelineMapper pipelineMapper,
                                    IngestionPipelineNodeMapper nodeMapper,
                                    AuthSessionService authSessionService,
                                    ObjectMapper objectMapper) {
        this.pipelineMapper = pipelineMapper;
        this.nodeMapper = nodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
    }

    public IngestionPipelineView create(IngestionPipelineCreateRequest request) {
        String name = normalizeName(request == null ? null : request.name());
        IngestionPipelineEntity entity = new IngestionPipelineEntity();
        entity.name = name;
        entity.description = blankToNull(request == null ? null : request.description());
        entity.createdBy = currentUserId();
        entity.updatedBy = currentUserId();
        entity.deleted = 0;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        pipelineMapper.insert(entity);
        replaceNodes(entity.id, request == null ? List.of() : request.nodes());
        return get(entity.id);
    }

    public IngestionPipelineView update(Long id, IngestionPipelineUpdateRequest request) {
        IngestionPipelineEntity entity = requirePipeline(id);
        if (StringUtils.hasText(request.name())) {
            entity.name = normalizeName(request.name());
        }
        if (request.description() != null) {
            entity.description = blankToNull(request.description());
        }
        entity.updatedBy = currentUserId();
        entity.updatedAt = LocalDateTime.now();
        pipelineMapper.updateById(entity);
        replaceNodes(id, request.nodes());
        return get(id);
    }

    public IngestionPipelineView get(Long id) {
        IngestionPipelineEntity entity = requirePipeline(id);
        return toView(entity, listNodes(id));
    }

    public IPage<IngestionPipelineView> page(long current, long size, String keyword) {
        Page<IngestionPipelineEntity> page = pipelineMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.max(size, 1)),
                new QueryWrapper<IngestionPipelineEntity>()
                        .eq("deleted", 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper.like("name", keyword).or().like("description", keyword))
                        .orderByDesc("updated_at")
        );
        Page<IngestionPipelineView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toViewWithFreshNodes).toList());
        return result;
    }

    public void delete(Long id) {
        pipelineMapper.deleteById(id);
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", id));
    }

    public List<IngestionPipelineNodeView> listNodes(Long pipelineId) {
        return nodeMapper.selectList(new QueryWrapper<IngestionPipelineNodeEntity>()
                        .eq("pipeline_id", pipelineId)
                        .eq("deleted", 0)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    private void replaceNodes(Long pipelineId, List<IngestionPipelineNodeRequest> requests) {
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", pipelineId));
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (IngestionPipelineNodeRequest request : requests) {
            IngestionPipelineNodeEntity entity = new IngestionPipelineNodeEntity();
            entity.pipelineId = pipelineId;
            entity.nodeId = normalizeNodeId(request.nodeId());
            entity.nodeType = normalizeNodeType(request.nodeType());
            entity.nextNodeId = blankToNull(request.nextNodeId());
            entity.settingsJson = toJson(request.settings());
            entity.conditionJson = toJson(request.condition());
            entity.createdBy = currentUserId();
            entity.updatedBy = currentUserId();
            entity.deleted = 0;
            entity.createdAt = LocalDateTime.now();
            entity.updatedAt = LocalDateTime.now();
            nodeMapper.insert(entity);
        }
    }

    private IngestionPipelineEntity requirePipeline(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("pipeline id 涓嶈兘涓虹┖");
        }
        IngestionPipelineEntity entity = pipelineMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new IllegalArgumentException("pipeline 涓嶅瓨鍦?");
        }
        return entity;
    }

    private IngestionPipelineView toViewWithFreshNodes(IngestionPipelineEntity entity) {
        return toView(entity, listNodes(entity.id));
    }

    private IngestionPipelineView toView(IngestionPipelineEntity entity, List<IngestionPipelineNodeView> nodes) {
        return new IngestionPipelineView(
                entity.id,
                entity.name,
                entity.description,
                entity.createdBy,
                entity.updatedBy,
                nodes,
                entity.createdAt,
                entity.updatedAt
        );
    }

    private IngestionPipelineNodeView toNodeView(IngestionPipelineNodeEntity entity) {
        return new IngestionPipelineNodeView(
                entity.id,
                entity.pipelineId,
                entity.nodeId,
                entity.nodeType,
                entity.nextNodeId,
                parseMap(entity.settingsJson),
                parseMap(entity.conditionJson),
                entity.createdBy,
                entity.updatedBy,
                entity.createdAt,
                entity.updatedAt
        );
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("pipeline name 涓嶈兘涓虹┖");
        }
        return value.trim();
    }

    private String normalizeNodeId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("nodeId 涓嶈兘涓虹┖");
        }
        return value.trim();
    }

    private String normalizeNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("nodeType 涓嶈兘涓虹┖");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long currentUserId() {
        try {
            return authSessionService.getCurrentSubjectId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of("raw", json);
        }
    }
}
