package com.personalblog.ragbackend.knowledge.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskNodeEntity;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskNodeView;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskResult;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskView;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskMapper;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskNodeMapper;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNodeLog;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionTaskService {
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;

    public IngestionTaskService(KnowledgeIngestionEngine knowledgeIngestionEngine,
                                IngestionTaskMapper taskMapper,
                                IngestionTaskNodeMapper taskNodeMapper,
                                AuthSessionService authSessionService,
                                ObjectMapper objectMapper) {
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.taskMapper = taskMapper;
        this.taskNodeMapper = taskNodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
    }

    public IngestionTaskResult execute(IngestionTaskCreateRequest request, MultipartFile file) {
        if (request == null) {
            throw new IllegalArgumentException("request 涓嶈兘涓虹┖");
        }
        if (!StringUtils.hasText(request.baseCode())) {
            throw new IllegalArgumentException("baseCode 涓嶈兘涓虹┖");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 涓嶈兘涓虹┖");
        }

        IngestionTaskEntity task = new IngestionTaskEntity();
        task.pipelineId = request.pipelineId();
        task.sourceType = blankToNull(request.sourceType());
        task.sourceLocation = blankToNull(request.sourceLocation());
        task.sourceFileName = blankToNull(request.sourceFileName());
        task.status = "RUNNING";
        task.chunkCount = 0;
        task.startedAt = LocalDateTime.now();
        task.createdBy = currentUserId();
        task.updatedBy = currentUserId();
        task.deleted = 0;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        taskMapper.insert(task);

        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(
                        request.baseCode(),
                        file,
                        KnowledgeIngestionMode.INGEST,
                        request.pipelineId(),
                        String.valueOf(task.id),
                        request.sourceType(),
                        request.sourceLocation(),
                        request.sourceFileName()
                )
        );

        persistResult(task, result);
        return new IngestionTaskResult(
                task.id,
                task.pipelineId,
                task.status,
                task.chunkCount,
                task.errorMessage
        );
    }

    public IngestionTaskView get(Long id) {
        IngestionTaskEntity task = requireTask(id);
        return toView(task, listNodes(task.id));
    }

    public IPage<IngestionTaskView> page(long current, long size, String status) {
        Page<IngestionTaskEntity> page = taskMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.max(size, 1)),
                new QueryWrapper<IngestionTaskEntity>()
                        .eq("deleted", 0)
                        .eq(StringUtils.hasText(status), "status", status)
                        .orderByDesc("created_at")
        );
        Page<IngestionTaskView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(task -> toView(task, listNodes(task.id))).toList());
        return result;
    }

    public List<IngestionTaskNodeView> listNodes(Long taskId) {
        return taskNodeMapper.selectList(new QueryWrapper<IngestionTaskNodeEntity>()
                        .eq("task_id", taskId)
                        .eq("deleted", 0)
                        .orderByAsc("node_order")
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    private void persistResult(IngestionTaskEntity task, KnowledgeIngestionResult result) {
        boolean success = result.ingestionSummary() != null && result.ingestionSummary().success();
        task.status = success ? "SUCCESS" : "FAILED";
        task.chunkCount = result.ingestionSummary() == null ? 0 : result.ingestionSummary().chunkCount();
        task.errorMessage = success ? null : result.ingestionSummary() == null ? "Ingestion failed" : result.ingestionSummary().errorMessage();
        task.completedAt = LocalDateTime.now();
        task.updatedBy = currentUserId();
        task.logsJson = toJson(result.nodeLogs());
        task.metadataJson = toJson(buildMetadata(task, result));
        task.updatedAt = LocalDateTime.now();
        taskMapper.updateById(task);
        saveNodeLogs(task, result.nodeLogs());
    }

    private void saveNodeLogs(IngestionTaskEntity task, List<KnowledgeIngestionNodeLog> nodeLogs) {
        if (nodeLogs == null || nodeLogs.isEmpty()) {
            return;
        }
        for (KnowledgeIngestionNodeLog log : nodeLogs) {
            IngestionTaskNodeEntity entity = new IngestionTaskNodeEntity();
            entity.taskId = task.id;
            entity.pipelineId = task.pipelineId;
            entity.nodeId = log.nodeType();
            entity.nodeType = log.nodeType();
            entity.nodeOrder = log.nodeOrder();
            entity.status = log.status();
            entity.durationMs = log.durationMs();
            entity.message = log.message();
            entity.errorMessage = log.errorMessage();
            entity.outputJson = log.outputJson();
            entity.deleted = 0;
            entity.createdAt = LocalDateTime.now();
            entity.updatedAt = LocalDateTime.now();
            taskNodeMapper.insert(entity);
        }
    }

    private IngestionTaskView toView(IngestionTaskEntity task, List<IngestionTaskNodeView> logs) {
        return new IngestionTaskView(
                task.id,
                task.pipelineId,
                task.kbId,
                task.docId,
                task.sourceType,
                task.sourceLocation,
                task.sourceFileName,
                task.status,
                task.chunkCount,
                task.errorMessage,
                logs,
                parseMap(task.metadataJson),
                task.startedAt,
                task.completedAt,
                task.createdBy,
                task.updatedBy,
                task.createdAt,
                task.updatedAt
        );
    }

    private IngestionTaskNodeView toNodeView(IngestionTaskNodeEntity entity) {
        return new IngestionTaskNodeView(
                entity.id,
                entity.taskId,
                entity.pipelineId,
                entity.nodeId,
                entity.nodeType,
                entity.nodeOrder,
                entity.status,
                entity.durationMs,
                entity.message,
                entity.errorMessage,
                parseMap(entity.outputJson),
                entity.createdAt,
                entity.updatedAt
        );
    }

    private IngestionTaskEntity requireTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("task id 涓嶈兘涓虹┖");
        }
        IngestionTaskEntity entity = taskMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new IllegalArgumentException("task 涓嶅瓨鍦?");
        }
        return entity;
    }

    private Long currentUserId() {
        try {
            return authSessionService.getCurrentSubjectId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildMetadata(IngestionTaskEntity task, KnowledgeIngestionResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineId", task.pipelineId);
        metadata.put("status", task.status);
        metadata.put("chunkCount", task.chunkCount);
        metadata.put("vectorIndexed", result.ingestionSummary() != null && result.ingestionSummary().vectorIndexed());
        metadata.put("collectionName", result.ingestionSummary() == null ? null : result.ingestionSummary().collectionName());
        metadata.put("embeddingModel", result.ingestionSummary() == null ? null : result.ingestionSummary().embeddingModel());
        return metadata;
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
