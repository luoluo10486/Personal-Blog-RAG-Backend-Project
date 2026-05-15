package com.personalblog.ragbackend.ingestion.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineNodeRequest;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionPipelineNodeVO;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionPipelineVO;
import com.personalblog.ragbackend.ingestion.dao.entity.IngestionPipelineEntity;
import com.personalblog.ragbackend.ingestion.dao.entity.IngestionPipelineNodeEntity;
import com.personalblog.ragbackend.ingestion.dao.mapper.IngestionPipelineMapper;
import com.personalblog.ragbackend.ingestion.dao.mapper.IngestionPipelineNodeMapper;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;
import com.personalblog.ragbackend.ingestion.service.IngestionPipelineService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class IngestionPipelineServiceImpl implements IngestionPipelineService {

    private final IngestionPipelineMapper pipelineMapper;
    private final IngestionPipelineNodeMapper nodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;

    public IngestionPipelineServiceImpl(IngestionPipelineMapper pipelineMapper,
                                        IngestionPipelineNodeMapper nodeMapper,
                                        AuthSessionService authSessionService,
                                        ObjectMapper objectMapper) {
        this.pipelineMapper = pipelineMapper;
        this.nodeMapper = nodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IngestionPipelineVO create(IngestionPipelineCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        String name = normalizeName(request.getName());
        IngestionPipelineEntity entity = new IngestionPipelineEntity();
        entity.name = name;
        entity.description = blankToNull(request.getDescription());
        entity.createdBy = currentUserId();
        entity.updatedBy = currentUserId();
        entity.deleted = 0;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        try {
            pipelineMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ClientException("pipeline name already exists");
        }
        replaceNodes(entity.id, request.getNodes());
        return get(String.valueOf(entity.id));
    }

    @Override
    public IngestionPipelineVO update(String id, IngestionPipelineUpdateRequest request) {
        Assert.notNull(request, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        Long pipelineId = parsePipelineId(id);
        IngestionPipelineEntity entity = requirePipeline(pipelineId);
        if (StringUtils.hasText(request.getName())) {
            entity.name = normalizeName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.description = blankToNull(request.getDescription());
        }
        entity.updatedBy = currentUserId();
        entity.updatedAt = LocalDateTime.now();
        pipelineMapper.updateById(entity);
        if (request.getNodes() != null) {
            replaceNodes(pipelineId, request.getNodes());
        }
        return get(id);
    }

    @Override
    public IngestionPipelineVO get(String id) {
        Long pipelineId = parsePipelineId(id);
        IngestionPipelineEntity entity = requirePipeline(pipelineId);
        return toView(entity, listNodes(pipelineId));
    }

    public IPage<IngestionPipelineVO> page(long current, long size, String keyword) {
        Page<IngestionPipelineEntity> page = pipelineMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.max(size, 1)),
                new QueryWrapper<IngestionPipelineEntity>()
                        .eq("deleted", 0)
                        .like(StringUtils.hasText(keyword), "name", keyword)
                        .orderByDesc("update_time")
        );
        Page<IngestionPipelineVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toViewWithFreshNodes).toList());
        return result;
    }

    @Override
    public void delete(String id) {
        Long pipelineId = parsePipelineId(id);
        IngestionPipelineEntity entity = requirePipeline(pipelineId);
        entity.deleted = 1;
        entity.updatedBy = currentUserId();
        pipelineMapper.deleteById(pipelineId);
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", pipelineId));
    }

    public List<IngestionPipelineNodeVO> listNodes(Long pipelineId) {
        return nodeMapper.selectList(new QueryWrapper<IngestionPipelineNodeEntity>()
                        .eq("pipeline_id", pipelineId)
                        .eq("deleted", 0)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    public PipelineDefinition getDefinition(Long id) {
        IngestionPipelineEntity entity = requirePipeline(id);
        List<NodeConfig> nodes = nodeMapper.selectList(new QueryWrapper<IngestionPipelineNodeEntity>()
                        .eq("pipeline_id", id)
                        .eq("deleted", 0)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeConfig)
                .toList();
        return PipelineDefinition.builder()
                .id(String.valueOf(entity.id))
                .name(entity.name)
                .description(entity.description)
                .nodes(nodes)
                .build();
    }

    @Override
    public PipelineDefinition getDefinition(String pipelineId) {
        return getDefinition(parsePipelineId(pipelineId));
    }

    @Override
    public IPage<IngestionPipelineVO> page(Page<IngestionPipelineVO> page, String keyword) {
        return page(page.getCurrent(), page.getSize(), keyword);
    }

    private void replaceNodes(Long pipelineId, List<IngestionPipelineNodeRequest> requests) {
        if (requests == null) {
            return;
        }
        nodeMapper.delete(new QueryWrapper<IngestionPipelineNodeEntity>().eq("pipeline_id", pipelineId));
        if (requests.isEmpty()) {
            return;
        }
        for (IngestionPipelineNodeRequest request : requests) {
            if (request == null) {
                continue;
            }
            IngestionPipelineNodeEntity entity = new IngestionPipelineNodeEntity();
            entity.pipelineId = pipelineId;
            entity.nodeId = normalizeNodeId(request.getNodeId());
            entity.nodeType = normalizeNodeType(request.getNodeType());
            entity.nextNodeId = blankToNull(request.getNextNodeId());
            entity.settingsJson = toJson(request.getSettings());
            entity.conditionJson = toJson(request.getCondition());
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
            throw new ClientException("pipeline id must not be blank");
        }
        IngestionPipelineEntity entity = pipelineMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new ClientException("pipeline not found");
        }
        return entity;
    }

    private IngestionPipelineVO toViewWithFreshNodes(IngestionPipelineEntity entity) {
        return toView(entity, listNodes(entity.id));
    }

    private IngestionPipelineVO toView(IngestionPipelineEntity entity, List<IngestionPipelineNodeVO> nodes) {
        IngestionPipelineVO vo = new IngestionPipelineVO();
        vo.setId(String.valueOf(entity.id));
        vo.setName(entity.name);
        vo.setDescription(entity.description);
        vo.setCreatedBy(stringify(entity.createdBy));
        vo.setNodes(nodes);
        vo.setCreateTime(java.sql.Timestamp.valueOf(entity.createdAt));
        vo.setUpdateTime(java.sql.Timestamp.valueOf(entity.updatedAt));
        return vo;
    }

    private IngestionPipelineNodeVO toNodeView(IngestionPipelineNodeEntity entity) {
        IngestionPipelineNodeVO vo = new IngestionPipelineNodeVO();
        vo.setId(String.valueOf(entity.id));
        vo.setNodeId(entity.nodeId);
        vo.setNodeType(normalizeNodeTypeForOutput(entity.nodeType));
        vo.setSettings(parseJson(entity.settingsJson));
        vo.setCondition(parseJson(entity.conditionJson));
        vo.setNextNodeId(blankToNull(entity.nextNodeId));
        return vo;
    }

    private NodeConfig toNodeConfig(IngestionPipelineNodeEntity entity) {
        return NodeConfig.builder()
                .nodeId(entity.nodeId)
                .nodeType(normalizeNodeType(entity.nodeType))
                .settings(parseJson(entity.settingsJson))
                .condition(parseJson(entity.conditionJson))
                .nextNodeId(blankToNull(entity.nextNodeId))
                .build();
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException("pipeline name must not be blank");
        }
        return value.trim();
    }

    private String normalizeNodeId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException("nodeId must not be blank");
        }
        return value.trim();
    }

    private String normalizeNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException("nodeType must not be blank");
        }
        return IngestionNodeType.fromValue(value).getValue();
    }

    private String normalizeNodeTypeForOutput(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return IngestionNodeType.fromValue(value).getValue();
        } catch (IllegalArgumentException exception) {
            return value.trim();
        }
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
            throw new ClientException("pipeline id must not be blank");
        }
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException exception) {
            throw new ClientException("pipeline not found");
        }
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
