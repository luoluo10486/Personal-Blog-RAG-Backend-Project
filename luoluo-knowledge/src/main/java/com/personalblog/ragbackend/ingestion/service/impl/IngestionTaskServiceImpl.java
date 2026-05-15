package com.personalblog.ragbackend.ingestion.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskNodeVO;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskVO;
import com.personalblog.ragbackend.ingestion.dao.entity.IngestionTaskEntity;
import com.personalblog.ragbackend.ingestion.dao.entity.IngestionTaskNodeEntity;
import com.personalblog.ragbackend.ingestion.dao.mapper.IngestionTaskMapper;
import com.personalblog.ragbackend.ingestion.dao.mapper.IngestionTaskNodeMapper;
import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.context.NodeLog;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionStatus;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;
import com.personalblog.ragbackend.ingestion.domain.result.IngestionResult;
import com.personalblog.ragbackend.ingestion.engine.IngestionEngine;
import com.personalblog.ragbackend.ingestion.service.IngestionPipelineService;
import com.personalblog.ragbackend.ingestion.service.IngestionTaskService;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import com.personalblog.ragbackend.rag.controller.request.DocumentSourceRequest;
import com.personalblog.ragbackend.rag.core.vector.VectorSpaceId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class IngestionTaskServiceImpl implements IngestionTaskService {

    private final IngestionEngine ingestionEngine;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;

    public IngestionTaskServiceImpl(IngestionEngine ingestionEngine,
                                    IngestionPipelineService ingestionPipelineService,
                                    IngestionTaskMapper taskMapper,
                                    IngestionTaskNodeMapper taskNodeMapper,
                                    AuthSessionService authSessionService,
                                    ObjectMapper objectMapper) {
        this.ingestionEngine = ingestionEngine;
        this.ingestionPipelineService = ingestionPipelineService;
        this.taskMapper = taskMapper;
        this.taskNodeMapper = taskNodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IngestionResult execute(IngestionTaskCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("request must not be blank"));
        DocumentSource source = toSource(request.getSource());
        return executeInternal(request.getPipelineId(), source, null, null, request.getVectorSpaceId());
    }

    @Override
    public IngestionResult upload(String pipelineId, MultipartFile file) {
        Assert.notNull(file, () -> new ClientException("file must not be blank"));
        try {
            byte[] bytes = file.getBytes();
            String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload.bin";
            String mimeType = MimeTypeDetector.detect(bytes, fileName);
            DocumentSource source = DocumentSource.builder()
                    .type(SourceType.FILE)
                    .location(fileName)
                    .fileName(fileName)
                    .build();
            return executeInternal(pipelineId, source, bytes, mimeType, null);
        } catch (Exception exception) {
            throw new ClientException("read file bytes failed: " + exception.getMessage());
        }
    }

    @Override
    public IngestionTaskVO get(String taskId) {
        IngestionTaskEntity task = requireTask(parseTaskId(taskId));
        return toView(task, readTaskLogs(task.logsJson));
    }

    @Override
    public IPage<IngestionTaskVO> page(Page<IngestionTaskVO> page, String status) {
        Page<IngestionTaskEntity> result = taskMapper.selectPage(
                new Page<>(page.getCurrent(), page.getSize()),
                new QueryWrapper<IngestionTaskEntity>()
                        .eq("deleted", 0)
                        .eq(StringUtils.hasText(status), "status", normalizeTaskStatus(status))
                        .orderByDesc("create_time")
        );
        Page<IngestionTaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(task -> toView(task, readTaskLogs(task.logsJson))).toList());
        return voPage;
    }

    @Override
    public List<IngestionTaskNodeVO> listNodes(String taskId) {
        Long parsedTaskId = parseTaskId(taskId);
        return taskNodeMapper.selectList(new QueryWrapper<IngestionTaskNodeEntity>()
                        .eq("task_id", parsedTaskId)
                        .eq("deleted", 0)
                        .orderByAsc("node_order")
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    private IngestionResult executeInternal(String pipelineId,
                                            DocumentSource source,
                                            byte[] rawBytes,
                                            String mimeType,
                                            VectorSpaceId vectorSpaceId) {
        String resolvedPipelineId = resolvePipelineId(pipelineId);
        PipelineDefinition pipeline = ingestionPipelineService.getDefinition(resolvedPipelineId);

        IngestionTaskEntity task = new IngestionTaskEntity();
        task.pipelineId = parsePipelineId(resolvedPipelineId);
        task.sourceType = source == null || source.getType() == null ? null : source.getType().getValue();
        task.sourceLocation = source == null ? null : source.getLocation();
        task.sourceFileName = source == null ? null : source.getFileName();
        task.status = IngestionStatus.RUNNING.getValue();
        task.chunkCount = 0;
        task.startedAt = LocalDateTime.now();
        task.createdBy = currentUserId();
        task.updatedBy = currentUserId();
        task.deleted = 0;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        taskMapper.insert(task);

        IngestionContext context = IngestionContext.builder()
                .taskId(stringify(task.id))
                .pipelineId(resolvedPipelineId)
                .source(source)
                .rawBytes(rawBytes)
                .mimeType(mimeType)
                .vectorSpaceId(vectorSpaceId)
                .logs(new ArrayList<>())
                .status(IngestionStatus.RUNNING)
                .build();

        IngestionContext result = ingestionEngine.execute(pipeline, context);
        saveNodeLogsWithOrder(task, pipeline, result.getLogs());
        updateTaskFromContext(task, result);
        return IngestionResult.builder()
                .taskId(stringify(task.id))
                .pipelineId(stringify(task.pipelineId))
                .status(result.getStatus())
                .chunkCount(result.getChunks() == null ? 0 : result.getChunks().size())
                .message(result.getError() == null ? "OK" : result.getError().getMessage())
                .build();
    }

    private void updateTaskFromContext(IngestionTaskEntity task,
                                       IngestionContext context) {
        task.status = context.getStatus() == null
                ? IngestionStatus.FAILED.getValue()
                : normalizeTaskStatus(context.getStatus().getValue());
        task.chunkCount = context.getChunks() == null ? 0 : context.getChunks().size();
        task.errorMessage = context.getError() == null ? null : context.getError().getMessage();
        task.completedAt = LocalDateTime.now();
        task.updatedBy = currentUserId();
        task.updatedAt = LocalDateTime.now();
        task.logsJson = toJson(buildLogSummary(context.getLogs()));
        task.metadataJson = toJson(buildTaskMetadata(context));
        taskMapper.updateById(task);
    }

    private void saveNodeLogsWithOrder(IngestionTaskEntity task, PipelineDefinition pipeline, List<NodeLog> nodeLogs) {
        if (nodeLogs == null || nodeLogs.isEmpty()) {
            return;
        }
        Map<String, Integer> nodeOrderMap = buildNodeOrderMap(pipeline);
        for (NodeLog log : nodeLogs) {
            IngestionTaskNodeEntity entity = new IngestionTaskNodeEntity();
            entity.taskId = task.id;
            entity.pipelineId = task.pipelineId;
            entity.nodeId = log.getNodeId();
            entity.nodeType = normalizeNodeTypeForOutput(log.getNodeType());
            entity.nodeOrder = nodeOrderMap.getOrDefault(log.getNodeId(), 0);
            entity.status = resolveNodeStatus(log);
            entity.durationMs = log.getDurationMs();
            entity.message = log.getMessage();
            entity.errorMessage = log.getError();
            entity.outputJson = truncateOutputJson(log.getOutput());
            entity.deleted = 0;
            entity.createdAt = LocalDateTime.now();
            entity.updatedAt = LocalDateTime.now();
            taskNodeMapper.insert(entity);
        }
    }

    private Map<String, Integer> buildNodeOrderMap(PipelineDefinition pipeline) {
        Map<String, Integer> orderMap = new HashMap<>();
        if (pipeline == null || pipeline.getNodes() == null || pipeline.getNodes().isEmpty()) {
            return orderMap;
        }
        Map<String, NodeConfig> nodeMap = new LinkedHashMap<>();
        for (NodeConfig node : pipeline.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            nodeMap.putIfAbsent(node.getNodeId(), node);
        }
        if (nodeMap.isEmpty()) {
            return orderMap;
        }
        Set<String> referenced = new HashSet<>();
        for (NodeConfig node : nodeMap.values()) {
            if (StringUtils.hasText(node.getNextNodeId())) {
                referenced.add(node.getNextNodeId());
            }
        }
        int order = 1;
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodeMap.keySet()) {
            if (referenced.contains(nodeId)) {
                continue;
            }
            String current = nodeId;
            while (StringUtils.hasText(current) && !visited.contains(current)) {
                orderMap.put(current, order++);
                visited.add(current);
                NodeConfig config = nodeMap.get(current);
                if (config == null) {
                    break;
                }
                current = config.getNextNodeId();
            }
        }
        for (String nodeId : nodeMap.keySet()) {
            if (!visited.contains(nodeId)) {
                orderMap.put(nodeId, order++);
            }
        }
        return orderMap;
    }

    private String resolveNodeStatus(NodeLog log) {
        if (log == null || !log.isSuccess()) {
            return "failed";
        }
        String message = log.getMessage();
        if (message != null && message.startsWith("Skipped:")) {
            return "skipped";
        }
        return "success";
    }

    private Map<String, Object> buildTaskMetadata(IngestionContext context) {
        Map<String, Object> metadata = new HashMap<>();
        if (context.getMetadata() != null) {
            metadata.putAll(context.getMetadata());
        }
        if (context.getKeywords() != null && !context.getKeywords().isEmpty()) {
            metadata.put("keywords", context.getKeywords());
        }
        if (context.getQuestions() != null && !context.getQuestions().isEmpty()) {
            metadata.put("questions", context.getQuestions());
        }
        return metadata;
    }

    private List<NodeLog> buildLogSummary(List<NodeLog> logs) {
        if (logs == null) {
            return List.of();
        }
        return logs.stream()
                .map(log -> NodeLog.builder()
                        .nodeId(log.getNodeId())
                        .nodeType(log.getNodeType())
                        .message(log.getMessage())
                        .durationMs(log.getDurationMs())
                        .success(log.isSuccess())
                        .error(log.getError())
                        .output(null)
                        .build())
                .toList();
    }

    private String truncateOutputJson(Object output) {
        if (output == null) {
            return null;
        }
        String json = toJson(output);
        if (json == null) {
            return null;
        }
        int maxSize = 1024 * 1024;
        if (json.length() <= maxSize) {
            return json;
        }
        String truncated = json.substring(0, maxSize - 100);
        return truncated + "... [output truncated, original size " + json.length() + " chars]";
    }

    private DocumentSource toSource(DocumentSourceRequest request) {
        Assert.notNull(request, () -> new ClientException("source must not be blank"));
        DocumentSource source = DocumentSource.builder()
                .type(request.getType())
                .location(request.getLocation())
                .fileName(request.getFileName())
                .credentials(request.getCredentials())
                .build();
        if (source.getType() == null) {
            throw new ClientException("source type must not be blank");
        }
        return source;
    }

    private IngestionTaskVO toView(IngestionTaskEntity task, List<NodeLog> logs) {
        IngestionTaskVO vo = new IngestionTaskVO();
        vo.setId(stringify(task.id));
        vo.setPipelineId(stringify(task.pipelineId));
        vo.setSourceType(normalizeSourceType(task.sourceType));
        vo.setSourceLocation(task.sourceLocation);
        vo.setSourceFileName(task.sourceFileName);
        vo.setStatus(normalizeTaskStatus(task.status));
        vo.setChunkCount(task.chunkCount);
        vo.setErrorMessage(task.errorMessage);
        vo.setLogs(logs);
        vo.setMetadata(parseMap(task.metadataJson));
        vo.setStartedAt(toDate(task.startedAt));
        vo.setCompletedAt(toDate(task.completedAt));
        vo.setCreatedBy(stringify(task.createdBy));
        vo.setCreateTime(toDate(task.createdAt));
        vo.setUpdateTime(toDate(task.updatedAt));
        return vo;
    }

    private List<NodeLog> readTaskLogs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<NodeLog>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private IngestionTaskNodeVO toNodeView(IngestionTaskNodeEntity entity) {
        IngestionTaskNodeVO vo = new IngestionTaskNodeVO();
        vo.setId(stringify(entity.id));
        vo.setTaskId(stringify(entity.taskId));
        vo.setPipelineId(stringify(entity.pipelineId));
        vo.setNodeId(entity.nodeId);
        vo.setNodeType(normalizeNodeTypeForOutput(entity.nodeType));
        vo.setNodeOrder(entity.nodeOrder);
        vo.setStatus(normalizeTaskStatus(entity.status));
        vo.setDurationMs(entity.durationMs);
        vo.setMessage(entity.message);
        vo.setErrorMessage(entity.errorMessage);
        vo.setOutput(parseMap(entity.outputJson));
        vo.setCreateTime(toDate(entity.createdAt));
        vo.setUpdateTime(toDate(entity.updatedAt));
        return vo;
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

    private IngestionTaskEntity requireTask(Long id) {
        if (id == null) {
            throw new ClientException("task id must not be blank");
        }
        IngestionTaskEntity entity = taskMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new ClientException("task not found");
        }
        return entity;
    }

    private Long parseTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new ClientException("task id must not be blank");
        }
        try {
            return Long.valueOf(taskId.trim());
        } catch (NumberFormatException exception) {
            throw new ClientException("task not found");
        }
    }

    private Long parsePipelineId(String pipelineId) {
        if (!StringUtils.hasText(pipelineId)) {
            throw new ClientException("pipelineId must not be blank");
        }
        try {
            return Long.valueOf(pipelineId.trim());
        } catch (NumberFormatException exception) {
            throw new ClientException("pipeline not found");
        }
    }

    private String resolvePipelineId(String pipelineId) {
        if (StringUtils.hasText(pipelineId)) {
            return pipelineId.trim();
        }
        throw new ClientException("pipelineId must not be blank");
    }

    private Long currentUserId() {
        try {
            return authSessionService.getCurrentSubjectId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalizeTaskStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return status;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        try {
            return IngestionStatus.fromValue(normalized).getValue();
        } catch (IllegalArgumentException ignored) {
            return status.trim();
        }
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return sourceType;
        }
        try {
            return SourceType.fromValue(sourceType).getValue();
        } catch (IllegalArgumentException ex) {
            return sourceType;
        }
    }

    private String normalizeNodeType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return nodeType;
        }
        try {
            return IngestionNodeType.fromValue(nodeType).getValue();
        } catch (IllegalArgumentException ex) {
            return nodeType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        }
    }

    private String normalizeNodeTypeForOutput(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return nodeType;
        }
        try {
            return IngestionNodeType.fromValue(nodeType).getValue();
        } catch (IllegalArgumentException ex) {
            return nodeType.trim();
        }
    }

    private byte[] getBytes(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ClientException("read file bytes failed: " + exception.getMessage());
        }
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

    private java.util.Date toDate(LocalDateTime time) {
        return time == null ? null : java.util.Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
