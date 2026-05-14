package com.personalblog.ragbackend.ingestion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.ingestion.controller.request.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskNodeVO;
import com.personalblog.ragbackend.ingestion.controller.vo.IngestionTaskVO;
import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.context.NodeLog;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionStatus;
import com.personalblog.ragbackend.ingestion.domain.enums.SourceType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;
import com.personalblog.ragbackend.ingestion.domain.result.IngestionResult;
import com.personalblog.ragbackend.ingestion.engine.IngestionEngine;
import com.personalblog.ragbackend.ingestion.service.IngestionPipelineService;
import com.personalblog.ragbackend.ingestion.util.MimeTypeDetector;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskNodeEntity;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskMapper;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskNodeMapper;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.rag.core.vector.VectorSpaceId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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

import software.amazon.awssdk.services.s3.S3Client;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class IngestionTaskServiceImpl implements com.personalblog.ragbackend.ingestion.service.IngestionTaskService {
    private final IngestionEngine ingestionEngine;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final S3Client s3Client;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public IngestionTaskServiceImpl(IngestionEngine ingestionEngine,
                                    IngestionPipelineService ingestionPipelineService,
                                    IngestionTaskMapper taskMapper,
                                    IngestionTaskNodeMapper taskNodeMapper,
                                    AuthSessionService authSessionService,
                                    ObjectMapper objectMapper,
                                    KnowledgeFileStorageService knowledgeFileStorageService,
                                    S3Client s3Client) {
        this.ingestionEngine = ingestionEngine;
        this.ingestionPipelineService = ingestionPipelineService;
        this.taskMapper = taskMapper;
        this.taskNodeMapper = taskNodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.s3Client = s3Client;
    }

    @Override
    public IngestionResult execute(IngestionTaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be blank");
        }
        if (!StringUtils.hasText(request.getPipelineId())) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        DocumentSource source = request.getSource();
        if (source == null || source.getType() == null || !StringUtils.hasText(source.getLocation())) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return executeInternal(request.getPipelineId(), source, null, null, request.getVectorSpaceId(), request.getMetadata());
    }

    @Override
    public IngestionResult upload(String pipelineId, MultipartFile file) {
        if (!StringUtils.hasText(pipelineId)) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be blank");
        }
        byte[] bytes = getBytes(file);
        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload.bin";
        String mimeType = MimeTypeDetector.detect(bytes, fileName);
        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FILE)
                .location(fileName)
                .fileName(fileName)
                .build();
        return executeInternal(pipelineId, source, bytes, mimeType, null, Map.of());
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
                                            VectorSpaceId vectorSpaceId,
                                            Map<String, Object> metadata) {
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
                .metadata(metadata)
                .vectorSpaceId(vectorSpaceId)
                .logs(new ArrayList<>())
                .status(IngestionStatus.RUNNING)
                .build();

        IngestionContext result = ingestionEngine.execute(pipeline, context);
        saveNodeLogsWithOrder(task, pipeline, result.getLogs());
        updateTaskFromContext(task, result, metadata, vectorSpaceId);
        return IngestionResult.builder()
                .taskId(stringify(task.id))
                .pipelineId(stringify(task.pipelineId))
                .status(result.getStatus())
                .chunkCount(result.getChunks() == null ? 0 : result.getChunks().size())
                .message(result.getError() == null ? "OK" : result.getError().getMessage())
                .build();
    }

    private void updateTaskFromContext(IngestionTaskEntity task,
                                       IngestionContext context,
                                       Map<String, Object> requestMetadata,
                                       VectorSpaceId vectorSpaceId) {
        task.status = normalizeTaskStatus(context.getStatus() == null ? null : context.getStatus().getValue());
        task.chunkCount = context.getChunks() == null ? 0 : context.getChunks().size();
        task.errorMessage = context.getError() == null ? null : context.getError().getMessage();
        task.completedAt = LocalDateTime.now();
        task.updatedBy = currentUserId();
        task.updatedAt = LocalDateTime.now();
        task.logsJson = toJson(buildLogSummary(context.getLogs()));
        task.metadataJson = toJson(buildTaskMetadata(context, requestMetadata, vectorSpaceId));
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
            entity.nodeType = normalizeNodeType(log.getNodeType());
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
        Map<String, com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig> nodeMap = new LinkedHashMap<>();
        for (com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig node : pipeline.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            nodeMap.putIfAbsent(node.getNodeId(), node);
        }
        if (nodeMap.isEmpty()) {
            return orderMap;
        }
        Set<String> referenced = new HashSet<>();
        for (com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig node : nodeMap.values()) {
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
                com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig config = nodeMap.get(current);
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

    private Map<String, Object> buildTaskMetadata(IngestionContext context,
                                                  Map<String, Object> requestMetadata,
                                                  VectorSpaceId vectorSpaceId) {
        Map<String, Object> metadata = new HashMap<>();
        if (requestMetadata != null) {
            metadata.putAll(requestMetadata);
        }
        if (context.getKeywords() != null && !context.getKeywords().isEmpty()) {
            metadata.put("keywords", context.getKeywords());
        }
        if (context.getQuestions() != null && !context.getQuestions().isEmpty()) {
            metadata.put("questions", context.getQuestions());
        }
        if (vectorSpaceId != null) {
            metadata.put("vectorSpaceId", vectorSpaceId);
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

    private IngestionResult persistAndConvertResult(IngestionContext context,
                                                    Map<String, Object> metadata,
                                                    VectorSpaceId vectorSpaceId) {
        IngestionTaskEntity task = new IngestionTaskEntity();
        task.pipelineId = parsePipelineId(context.getPipelineId());
        task.sourceType = context.getSource() == null || context.getSource().getType() == null ? null : context.getSource().getType().getValue();
        task.sourceLocation = context.getSource() == null ? null : context.getSource().getLocation();
        task.sourceFileName = context.getSource() == null ? null : context.getSource().getFileName();
        task.status = normalizeTaskStatus(context.getStatus() == null ? null : context.getStatus().getValue());
        task.chunkCount = context.getChunks() == null ? 0 : context.getChunks().size();
        task.errorMessage = context.getError() == null ? null : context.getError().getMessage();
        task.logsJson = toJson(context.getLogs());
        task.metadataJson = toJson(buildMetadata(context, metadata, vectorSpaceId));
        task.startedAt = LocalDateTime.now();
        task.completedAt = LocalDateTime.now();
        task.createdBy = currentUserId();
        task.updatedBy = currentUserId();
        task.deleted = 0;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        taskMapper.insert(task);
        saveNodeLogs(task, context.getLogs());
        return new IngestionResult(
                stringify(task.id),
                stringify(task.pipelineId),
                context.getStatus(),
                task.chunkCount,
                context.getError() == null ? "OK" : context.getError().getMessage()
        );
    }

    private MultipartFile resolveSourceFile(DocumentSource source) {
        String normalizedType = source.getType() == null ? "file" : source.getType().getValue();
        return switch (normalizedType) {
            case "file" -> readLocalFile(source);
            case "s3" -> readS3File(source);
            case "url" -> downloadRemoteSource(source);
            case "feishu" -> downloadFeishuSource(source);
            default -> throw new IllegalArgumentException("Unsupported source type: " + source.getType());
        };
    }

    private MultipartFile readLocalFile(DocumentSource source) {
        String location = source.getLocation();
        try {
            Path path = location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location);
            byte[] bytes = Files.readAllBytes(path);
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : path.getFileName().toString();
            return new SimpleMultipartFile(fileName, fileName, "application/octet-stream", bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("read local file failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile readS3File(DocumentSource source) {
        try (InputStream inputStream = knowledgeFileStorageService.openStream(source.getLocation())) {
            byte[] bytes = inputStream.readAllBytes();
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : resolveFileNameFromUrl(source.getLocation());
            return new SimpleMultipartFile(fileName, fileName, "application/octet-stream", bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("read s3 file failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile downloadRemoteSource(DocumentSource source) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.getLocation())).GET();
            if (source.getCredentials() != null) {
                source.getCredentials().forEach(builder::header);
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("download remote source failed, HTTP status: " + response.statusCode());
            }
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : resolveFileNameFromUrl(source.getLocation());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new SimpleMultipartFile(fileName, fileName, contentType, response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("download remote source failed: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("download remote source failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile downloadFeishuSource(DocumentSource source) {
        try {
            String accessToken = resolveFeishuAccessToken(source.getCredentials());
            if (isFeishuDocxUrl(source.getLocation())) {
                String docToken = extractFeishuDocToken(source.getLocation());
                HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("https://open.feishu.cn/open-apis/docx/v1/documents/" + docToken + "/raw_content"))
                        .GET();
                if (StringUtils.hasText(accessToken)) {
                    builder.header("Authorization", "Bearer " + accessToken);
                }
                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                ensureSuccess(response.statusCode(), "download feishu document failed");
                String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : docToken + ".txt";
                return new SimpleMultipartFile(fileName, fileName, "text/plain", response.body());
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.getLocation())).GET();
            if (StringUtils.hasText(accessToken)) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), "download feishu resource failed");
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName() : resolveFileNameFromUrl(source.getLocation());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new SimpleMultipartFile(fileName, fileName, contentType, response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("download feishu source failed: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("download feishu source failed: " + exception.getMessage(), exception);
        }
    }

    private String resolveFeishuAccessToken(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        return firstNonBlank(credentials.get("tenantAccessToken"), credentials.get("accessToken"));
    }

    private String resolveFileNameFromUrl(String location) {
        if (!StringUtils.hasText(location)) {
            return "upload.bin";
        }
        try {
            String path = URI.create(location).getPath();
            if (!StringUtils.hasText(path)) {
                return "upload.bin";
            }
            String fileName = Path.of(path).getFileName().toString();
            return StringUtils.hasText(fileName) ? fileName : "upload.bin";
        } catch (Exception ignored) {
            return "upload.bin";
        }
    }

    private boolean isFeishuDocxUrl(String location) {
        return location != null && (location.contains("/docx/") || location.contains("/docs/"));
    }

    private String extractFeishuDocToken(String location) {
        String[] parts = location.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("docx".equalsIgnoreCase(parts[i]) || "docs".equalsIgnoreCase(parts[i])) {
                if (i + 1 < parts.length) {
                    String token = parts[i + 1];
                    int queryIndex = token.indexOf('?');
                    return queryIndex > 0 ? token.substring(0, queryIndex) : token;
                }
            }
        }
        throw new IllegalArgumentException("unable to parse feishu document token");
    }

    private void ensureSuccess(int statusCode, String message) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalArgumentException(message + ", HTTP status: " + statusCode);
        }
    }

    private void saveNodeLogs(IngestionTaskEntity task, List<NodeLog> nodeLogs) {
        if (nodeLogs == null || nodeLogs.isEmpty()) {
            return;
        }
        for (NodeLog log : nodeLogs) {
            IngestionTaskNodeEntity entity = new IngestionTaskNodeEntity();
            entity.taskId = task.id;
            entity.pipelineId = task.pipelineId;
            entity.nodeId = log.getNodeId();
            entity.nodeType = normalizeNodeType(log.getNodeType());
            entity.nodeOrder = 0;
            entity.status = log.isSuccess() ? "success" : "failed";
            entity.durationMs = log.getDurationMs();
            entity.message = log.getMessage();
            entity.errorMessage = log.getError();
            entity.outputJson = toJson(log.getOutput());
            entity.deleted = 0;
            entity.createdAt = LocalDateTime.now();
            entity.updatedAt = LocalDateTime.now();
            taskNodeMapper.insert(entity);
        }
    }

    private IngestionTaskVO toView(IngestionTaskEntity task, List<NodeLog> logs) {
        IngestionTaskVO vo = new IngestionTaskVO();
        vo.setId(stringify(task.id));
        vo.setPipelineId(stringify(task.pipelineId));
        vo.setSourceType(task.sourceType);
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
        vo.setNodeType(normalizeNodeType(entity.nodeType));
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

    private IngestionTaskEntity requireTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("task id must not be blank");
        }
        IngestionTaskEntity entity = taskMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new IllegalArgumentException("task not found");
        }
        return entity;
    }

    private Long parseTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("task id must not be blank");
        }
        try {
            return Long.valueOf(taskId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("task not found");
        }
    }

    private Long parsePipelineId(String pipelineId) {
        if (!StringUtils.hasText(pipelineId)) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        try {
            return Long.valueOf(pipelineId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pipeline not found");
        }
    }

    private String resolvePipelineId(String pipelineId) {
        if (StringUtils.hasText(pipelineId)) {
            return pipelineId.trim();
        }
        throw new IllegalArgumentException("pipelineId must not be blank");
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
            return normalized;
        }
    }

    private String normalizeNodeType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return nodeType;
        }
        try {
            return IngestionNodeType.fromValue(nodeType).getValue();
        } catch (IllegalArgumentException ignored) {
            return nodeType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        }
    }

    private byte[] getBytes(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("read file bytes failed: " + exception.getMessage(), exception);
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

    private Map<String, Object> buildMetadata(IngestionContext context, Map<String, Object> requestMetadata, VectorSpaceId vectorSpaceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (requestMetadata != null) {
            metadata.putAll(requestMetadata);
        }
        if (context.getKeywords() != null && !context.getKeywords().isEmpty()) {
            metadata.put("keywords", context.getKeywords());
        }
        if (context.getQuestions() != null && !context.getQuestions().isEmpty()) {
            metadata.put("questions", context.getQuestions());
        }
        metadata.put("vectorSpaceId", vectorSpaceId);
        metadata.put("pipelineId", context.getPipelineId());
        metadata.put("status", context.getStatus() == null ? null : context.getStatus().getValue());
        metadata.put("chunkCount", context.getChunks() == null ? 0 : context.getChunks().size());
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

    private java.util.Date toDate(LocalDateTime time) {
        return time == null ? null : java.util.Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static final class SimpleMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private SimpleMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.write(dest, content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
