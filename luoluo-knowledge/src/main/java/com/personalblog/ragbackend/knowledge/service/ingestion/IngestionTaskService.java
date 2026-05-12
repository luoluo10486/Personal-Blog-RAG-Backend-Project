package com.personalblog.ragbackend.knowledge.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.knowledge.domain.enums.SourceType;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskNodeEntity;
import com.personalblog.ragbackend.knowledge.dto.ingestion.DocumentSourceRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskLogView;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskNodeView;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskResult;
import com.personalblog.ragbackend.knowledge.dto.ingestion.IngestionTaskView;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskMapper;
import com.personalblog.ragbackend.knowledge.mapper.IngestionTaskNodeMapper;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNodeLog;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import com.personalblog.ragbackend.knowledge.service.ingest.pipeline.IngestionPipelineDefinition;
import com.personalblog.ragbackend.rag.core.vector.VectorSpaceId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import software.amazon.awssdk.services.s3.S3Client;

@Service
public class IngestionTaskService {
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final S3Client s3Client;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public IngestionTaskService(KnowledgeIngestionEngine knowledgeIngestionEngine,
                                IngestionPipelineService ingestionPipelineService,
                                IngestionTaskMapper taskMapper,
                                IngestionTaskNodeMapper taskNodeMapper,
                                AuthSessionService authSessionService,
                                ObjectMapper objectMapper,
                                KnowledgeFileStorageService knowledgeFileStorageService,
                                S3Client s3Client) {
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.ingestionPipelineService = ingestionPipelineService;
        this.taskMapper = taskMapper;
        this.taskNodeMapper = taskNodeMapper;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.s3Client = s3Client;
    }

    public IngestionTaskResult execute(IngestionTaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be blank");
        }
        if (!StringUtils.hasText(request.pipelineId())) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        DocumentSourceRequest source = request.source();
        if (source == null || source.type() == null || !StringUtils.hasText(source.location())) {
            throw new IllegalArgumentException("source must not be blank");
        }

        Long pipelineId = parsePipelineId(request.pipelineId());
        IngestionPipelineDefinition pipeline = ingestionPipelineService.getDefinition(pipelineId);
        String normalizedType = normalizeSourceType(source.type());
        MultipartFile file = resolveSourceFile(source, normalizedType);
        String baseCode = resolveBaseCode(request, pipeline);
        String sourceFileUrl = isHttpSource(normalizedType) ? source.location() : null;
        return executeInternal(
                baseCode,
                pipelineId,
                pipeline,
                normalizedType,
                source,
                request.metadata(),
                request.vectorSpaceId(),
                file,
                sourceFileUrl
        );
    }

    public IngestionTaskResult upload(String pipelineId, MultipartFile file) {
        if (!StringUtils.hasText(pipelineId)) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be blank");
        }
        Long parsedPipelineId = parsePipelineId(pipelineId);
        DocumentSourceRequest source = new DocumentSourceRequest(
                SourceType.FILE,
                file.getOriginalFilename(),
                file.getOriginalFilename(),
                Map.of()
        );
        return executeInternal(
                pipelineId,
                parsedPipelineId,
                ingestionPipelineService.getDefinition(parsedPipelineId),
                "file",
                source,
                Map.of(),
                null,
                file,
                null
        );
    }

    public IngestionTaskView get(String id) {
        IngestionTaskEntity task = requireTask(parseTaskId(id));
        return toView(task, readTaskLogs(task.logsJson));
    }

    public IPage<IngestionTaskView> page(long current, long size, String status) {
        Page<IngestionTaskEntity> page = taskMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.max(size, 1)),
                new QueryWrapper<IngestionTaskEntity>()
                        .eq("deleted", 0)
                        .eq(StringUtils.hasText(status), "status", normalizeTaskStatus(status))
                        .orderByDesc("create_time")
        );
        Page<IngestionTaskView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(task -> toView(task, readTaskLogs(task.logsJson))).toList());
        return result;
    }

    public List<IngestionTaskNodeView> listNodes(String taskId) {
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

    private IngestionTaskResult executeInternal(String baseCode,
                                                Long pipelineId,
                                                IngestionPipelineDefinition pipeline,
                                                String normalizedSourceType,
                                                DocumentSourceRequest source,
                                                Map<String, Object> metadata,
                                                VectorSpaceId vectorSpaceId,
                                                MultipartFile file,
                                                String sourceFileUrl) {
        IngestionTaskEntity task = new IngestionTaskEntity();
        task.pipelineId = pipelineId;
        task.sourceType = blankToNull(normalizedSourceType);
        task.sourceLocation = blankToNull(source.location());
        task.sourceFileName = blankToNull(resolveFileName(source, file));
        task.status = "running";
        task.chunkCount = 0;
        task.startedAt = LocalDateTime.now();
        task.createdBy = currentUserId();
        task.updatedBy = currentUserId();
        task.deleted = 0;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = LocalDateTime.now();
        taskMapper.insert(task);

        KnowledgeIngestionRequest ingestionRequest = new KnowledgeIngestionRequest(
                baseCode,
                file,
                KnowledgeIngestionMode.INGEST,
                pipelineId,
                String.valueOf(task.id),
                normalizedSourceType,
                source.location(),
                resolveFileName(source, file),
                sourceFileUrl
        );
        try {
            KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(pipeline, ingestionRequest);
            persistResult(task, result, metadata, vectorSpaceId);
            return new IngestionTaskResult(
                    stringify(task.id),
                    stringify(task.pipelineId),
                    task.status,
                    task.chunkCount,
                    task.errorMessage
            );
        } catch (RuntimeException exception) {
            markTaskFailed(task, exception.getMessage(), metadata, vectorSpaceId);
            return new IngestionTaskResult(
                    stringify(task.id),
                    stringify(task.pipelineId),
                    task.status,
                    task.chunkCount,
                    task.errorMessage
            );
        }
    }

    private MultipartFile resolveSourceFile(DocumentSourceRequest source, String normalizedType) {
        return switch (normalizedType) {
            case "file" -> readLocalFile(source);
            case "s3" -> readS3File(source);
            case "url" -> downloadRemoteSource(source);
            case "feishu" -> downloadFeishuSource(source);
            default -> throw new IllegalArgumentException("Unsupported source type: " + source.type());
        };
    }

    private MultipartFile readLocalFile(DocumentSourceRequest source) {
        String location = source.location();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("source.location must not be blank");
        }
        try {
            Path path = location.startsWith("file:")
                    ? Path.of(URI.create(location))
                    : Path.of(location);
            byte[] bytes = Files.readAllBytes(path);
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : path.getFileName().toString();
            return new SimpleMultipartFile(fileName, fileName, "application/octet-stream", bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("read local file failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile readS3File(DocumentSourceRequest source) {
        try (InputStream inputStream = knowledgeFileStorageService.openStream(source.location())) {
            byte[] bytes = inputStream.readAllBytes();
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : resolveFileNameFromUrl(source.location());
            return new SimpleMultipartFile(fileName, fileName, "application/octet-stream", bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("read s3 file failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile downloadRemoteSource(DocumentSourceRequest source) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.location())).GET();
            if (source.credentials() != null) {
                source.credentials().forEach(builder::header);
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("download remote source failed, HTTP status: " + response.statusCode());
            }
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : resolveFileNameFromUrl(source.location());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new SimpleMultipartFile(fileName, fileName, contentType, response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("download remote source failed: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("download remote source failed: " + exception.getMessage(), exception);
        }
    }

    private MultipartFile downloadFeishuSource(DocumentSourceRequest source) {
        try {
            String accessToken = resolveFeishuAccessToken(source.credentials());
            if (isFeishuDocxUrl(source.location())) {
                String docToken = extractFeishuDocToken(source.location());
                HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("https://open.feishu.cn/open-apis/docx/v1/documents/" + docToken + "/raw_content"))
                        .GET();
                if (StringUtils.hasText(accessToken)) {
                    builder.header("Authorization", "Bearer " + accessToken);
                }
                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                ensureSuccess(response.statusCode(), "download feishu document failed");
                String content = extractFeishuContent(response.body());
                String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : docToken + ".txt";
                return new SimpleMultipartFile(fileName, fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8));
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.location())).GET();
            if (StringUtils.hasText(accessToken)) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), "download feishu resource failed");
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : resolveFileNameFromUrl(source.location());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new SimpleMultipartFile(fileName, fileName, contentType, response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("download feishu source failed: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("download feishu source failed: " + exception.getMessage(), exception);
        }
    }

    private String resolveFileName(DocumentSourceRequest source, MultipartFile file) {
        if (StringUtils.hasText(source.fileName())) {
            return source.fileName().trim();
        }
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }
        if (StringUtils.hasText(source.location())) {
            return resolveFileNameFromUrl(source.location());
        }
        return "upload.bin";
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

    private boolean isHttpSource(String normalizedType) {
        return "url".equals(normalizedType) || "feishu".equals(normalizedType);
    }

    private void ensureSuccess(int statusCode, String message) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalArgumentException(message + ", HTTP status: " + statusCode);
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

    private String resolveFeishuAccessToken(Map<String, String> credentials) throws IOException, InterruptedException {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        String token = firstNonBlank(credentials.get("tenantAccessToken"), credentials.get("accessToken"));
        if (StringUtils.hasText(token)) {
            return token;
        }
        String appId = credentials.get("app_id");
        String appSecret = credentials.get("app_secret");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            return null;
        }
        String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), "get feishu token failed");
        return extractJsonStringField(response.body(), "tenant_access_token");
    }

    private String extractFeishuContent(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        String content = extractJsonStringField(json, "content");
        return content == null ? json : content;
    }

    private String extractJsonStringField(String json, String field) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Object direct = parsed.get(field);
            if (direct instanceof String value && !value.isBlank()) {
                return value;
            }
            Object data = parsed.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object nested = dataMap.get(field);
                if (nested instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void persistResult(IngestionTaskEntity task,
                               KnowledgeIngestionResult result,
                               Map<String, Object> metadata,
                               VectorSpaceId vectorSpaceId) {
        boolean success = result.ingestionSummary() != null && result.ingestionSummary().success();
        task.status = success ? "completed" : "failed";
        task.chunkCount = result.ingestionSummary() == null ? 0 : result.ingestionSummary().chunkCount();
        task.errorMessage = success ? null : result.ingestionSummary() == null ? "Ingestion failed" : result.ingestionSummary().errorMessage();
        task.completedAt = LocalDateTime.now();
        task.updatedBy = currentUserId();
        task.logsJson = toJson(result.nodeLogs());
        task.metadataJson = toJson(buildMetadata(task, result, metadata, vectorSpaceId));
        task.updatedAt = LocalDateTime.now();
        taskMapper.updateById(task);
        saveNodeLogs(task, result.nodeLogs());
    }

    private void markTaskFailed(IngestionTaskEntity task,
                                String message,
                                Map<String, Object> metadata,
                                VectorSpaceId vectorSpaceId) {
        task.status = "failed";
        task.chunkCount = 0;
        task.errorMessage = StringUtils.hasText(message) ? message : "Ingestion failed";
        task.completedAt = LocalDateTime.now();
        task.updatedBy = currentUserId();
        Map<String, Object> failureMetadata = new LinkedHashMap<>();
        failureMetadata.put("pipelineId", task.pipelineId);
        failureMetadata.put("status", task.status);
        failureMetadata.put("chunkCount", task.chunkCount);
        failureMetadata.put("requestMetadata", metadata == null ? Map.of() : metadata);
        failureMetadata.put("vectorSpaceId", vectorSpaceId == null ? Map.of() : vectorSpaceId);
        task.metadataJson = toJson(failureMetadata);
        task.updatedAt = LocalDateTime.now();
        taskMapper.updateById(task);
    }

    private void saveNodeLogs(IngestionTaskEntity task, List<KnowledgeIngestionNodeLog> nodeLogs) {
        if (nodeLogs == null || nodeLogs.isEmpty()) {
            return;
        }
        for (KnowledgeIngestionNodeLog log : nodeLogs) {
            IngestionTaskNodeEntity entity = new IngestionTaskNodeEntity();
            entity.taskId = task.id;
            entity.pipelineId = task.pipelineId;
            entity.nodeId = log.nodeId();
            entity.nodeType = normalizeNodeType(log.nodeType());
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

    private IngestionTaskView toView(IngestionTaskEntity task, List<IngestionTaskLogView> logs) {
        return new IngestionTaskView(
                stringify(task.id),
                stringify(task.pipelineId),
                task.sourceType,
                task.sourceLocation,
                task.sourceFileName,
                normalizeTaskStatus(task.status),
                task.chunkCount,
                task.errorMessage,
                logs,
                parseMap(task.metadataJson),
                task.startedAt,
                task.completedAt,
                stringify(task.createdBy),
                task.createdAt,
                task.updatedAt
        );
    }

    private List<IngestionTaskLogView> readTaskLogs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<KnowledgeIngestionNodeLog> nodeLogs = objectMapper.readValue(raw, new TypeReference<List<KnowledgeIngestionNodeLog>>() {});
            return nodeLogs.stream().map(this::toTaskLogView).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private IngestionTaskLogView toTaskLogView(KnowledgeIngestionNodeLog log) {
        return new IngestionTaskLogView(
                log.nodeId(),
                normalizeNodeType(log.nodeType()),
                log.message(),
                log.durationMs(),
                !"failed".equalsIgnoreCase(log.status()),
                log.errorMessage(),
                parseMap(log.outputJson())
        );
    }

    private IngestionTaskNodeView toNodeView(IngestionTaskNodeEntity entity) {
        return new IngestionTaskNodeView(
                stringify(entity.id),
                stringify(entity.taskId),
                stringify(entity.pipelineId),
                entity.nodeId,
                normalizeNodeType(entity.nodeType),
                entity.nodeOrder,
                normalizeNodeStatus(entity.status),
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

    private Long currentUserId() {
        try {
            return authSessionService.getCurrentSubjectId();
        } catch (RuntimeException ignored) {
            return null;
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

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return "file";
        }
        return sourceType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSourceType(SourceType sourceType) {
        if (sourceType == null) {
            return "file";
        }
        return normalizeSourceType(sourceType.getValue());
    }

    private String normalizeNodeType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return nodeType;
        }
        String normalized = nodeType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "fetcher", "parser", "enhancer", "chunker", "enricher", "indexer" -> normalized;
            case "plan" -> "fetcher";
            case "parse" -> "parser";
            case "chunk", "persist", "embed" -> "chunker";
            case "index", "finalize" -> "indexer";
            default -> normalized;
        };
    }

    private String normalizeTaskStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return status;
        }
        return status.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String normalizeNodeStatus(String status) {
        return normalizeTaskStatus(status);
    }

    private String resolveBaseCode(IngestionTaskCreateRequest request, IngestionPipelineDefinition pipeline) {
        if (request != null && request.vectorSpaceId() != null && StringUtils.hasText(request.vectorSpaceId().logicalName())) {
            return request.vectorSpaceId().logicalName();
        }
        if (pipeline != null && StringUtils.hasText(pipeline.name())) {
            return pipeline.name();
        }
        return request == null ? "default" : request.pipelineId();
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

    private Map<String, Object> buildMetadata(IngestionTaskEntity task,
                                              KnowledgeIngestionResult result,
                                              Map<String, Object> requestMetadata,
                                              VectorSpaceId vectorSpaceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineId", task.pipelineId);
        metadata.put("status", task.status);
        metadata.put("chunkCount", task.chunkCount);
        metadata.put("vectorIndexed", result.ingestionSummary() != null && result.ingestionSummary().vectorIndexed());
        metadata.put("collectionName", result.ingestionSummary() == null ? null : result.ingestionSummary().collectionName());
        metadata.put("embeddingModel", result.ingestionSummary() == null ? null : result.ingestionSummary().embeddingModel());
        metadata.put("requestMetadata", requestMetadata == null ? Map.of() : requestMetadata);
        metadata.put("vectorSpaceId", vectorSpaceId == null ? Map.of() : vectorSpaceId);
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

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
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
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
