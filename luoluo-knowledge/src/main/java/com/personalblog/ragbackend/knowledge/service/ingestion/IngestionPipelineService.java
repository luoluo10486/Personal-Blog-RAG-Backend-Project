package com.personalblog.ragbackend.knowledge.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.personalblog.ragbackend.knowledge.service.ingest.pipeline.IngestionPipelineDefinition;
import com.personalblog.ragbackend.knowledge.service.ingest.pipeline.IngestionPipelineNodeConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
        return get(String.valueOf(entity.id));
    }

    public IngestionPipelineView update(String id, IngestionPipelineUpdateRequest request) {
        Long pipelineId = parsePipelineId(id);
        IngestionPipelineEntity entity = requirePipeline(pipelineId);
        if (StringUtils.hasText(request.name())) {
            entity.name = normalizeName(request.name());
        }
        if (request.description() != null) {
            entity.description = blankToNull(request.description());
        }
        entity.updatedBy = currentUserId();
        entity.updatedAt = LocalDateTime.now();
        pipelineMapper.updateById(entity);
        replaceNodes(pipelineId, request.nodes());
        return get(id);
    }

    public IngestionPipelineView get(String id) {
        Long pipelineId = parsePipelineId(id);
        IngestionPipelineEntity entity = requirePipeline(pipelineId);
        return toView(entity, listNodes(pipelineId));
    }

    public IPage<IngestionPipelineView> page(long current, long size, String keyword) {
        Page<IngestionPipelineEntity> page = pipelineMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.max(size, 1)),
                new QueryWrapper<IngestionPipelineEntity>()
                        .eq("deleted", 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper.like("name", keyword).or().like("description", keyword))
                        .orderByDesc("update_time")
        );
        Page<IngestionPipelineView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toViewWithFreshNodes).toList());
        return result;
    }

    public void delete(String id) {
        Long pipelineId = parsePipelineId(id);
        requirePipeline(pipelineId);
        pipelineMapper.deleteById(pipelineId);
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", pipelineId));
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

    public IngestionPipelineDefinition getDefinition(Long id) {
        IngestionPipelineEntity entity = requirePipeline(id);
        List<IngestionPipelineNodeConfig> nodes = nodeMapper.selectList(new QueryWrapper<IngestionPipelineNodeEntity>()
                        .eq("pipeline_id", id)
                        .eq("deleted", 0)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeConfig)
                .toList();
        return new IngestionPipelineDefinition(
                String.valueOf(entity.id),
                entity.name,
                entity.description,
                nodes
        );
    }

    private void replaceNodes(Long pipelineId, List<IngestionPipelineNodeRequest> requests) {
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", pipelineId));
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (IngestionPipelineNodeRequest request : requests) {
            if (request == null) {
                continue;
            }
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
            throw new IllegalArgumentException("pipeline id must not be blank");
        }
        IngestionPipelineEntity entity = pipelineMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new IllegalArgumentException("pipeline not found");
        }
        return entity;
    }

    private IngestionPipelineView toViewWithFreshNodes(IngestionPipelineEntity entity) {
        return toView(entity, listNodes(entity.id));
    }

    private IngestionPipelineView toView(IngestionPipelineEntity entity, List<IngestionPipelineNodeView> nodes) {
        return new IngestionPipelineView(
                String.valueOf(entity.id),
                entity.name,
                entity.description,
                stringify(entity.createdBy),
                nodes,
                entity.createdAt,
                entity.updatedAt
        );
    }

    private IngestionPipelineNodeView toNodeView(IngestionPipelineNodeEntity entity) {
        return new IngestionPipelineNodeView(
                String.valueOf(entity.id),
                entity.nodeId,
                normalizeNodeType(entity.nodeType),
                parseJson(entity.settingsJson),
                parseJson(entity.conditionJson),
                blankToNull(entity.nextNodeId)
        );
    }

    private IngestionPipelineNodeConfig toNodeConfig(IngestionPipelineNodeEntity entity) {
        return new IngestionPipelineNodeConfig(
                entity.nodeId,
                normalizeNodeType(entity.nodeType),
                parseMap(entity.settingsJson),
                parseMap(entity.conditionJson),
                blankToNull(entity.nextNodeId)
        );
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("pipeline name must not be blank");
        }
        return value.trim();
    }

    private String normalizeNodeId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        return value.trim();
    }

    private String normalizeNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("nodeType must not be blank");
        }
        String normalized = value.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "fetcher", "parser", "enhancer", "chunker", "enricher", "indexer" -> normalized;
            case "plan" -> "fetcher";
            case "parse" -> "parser";
            case "chunk", "persist", "embed" -> "chunker";
            case "index", "finalize" -> "indexer";
            default -> throw new IllegalArgumentException("Unknown ingestion node type: " + value);
        };
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

    private String toJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.toString();
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

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parsePipelineId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("pipeline id must not be blank");
        }
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pipeline not found");
        }
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
