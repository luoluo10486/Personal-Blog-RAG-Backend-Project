package com.personalblog.ragbackend.knowledge.service.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionTaskNodeEntity;
import com.personalblog.ragbackend.knowledge.dto.ingestion.DocumentSourceRequest;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IngestionTaskService {
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final IngestionTaskMapper taskMapper;
    private final IngestionTaskNodeMapper taskNodeMapper;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

    public IngestionTaskResult execute(IngestionTaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (!StringUtils.hasText(request.pipelineId())) {
            throw new IllegalArgumentException("pipelineId 不能为空");
        }
        DocumentSourceRequest source = request.source();
        if (source == null || !StringUtils.hasText(source.type()) || !StringUtils.hasText(source.location())) {
            throw new IllegalArgumentException("source 不能为空");
        }

        String normalizedType = normalizeSourceType(source.type());
        MultipartFile file = resolveSourceFile(source, normalizedType);
        Long pipelineId = parsePipelineId(request.pipelineId());
        String baseCode = request.pipelineId();
        String sourceFileUrl = isHttpSource(normalizedType) ? source.location() : null;
        return executeInternal(
                baseCode,
                pipelineId,
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
            throw new IllegalArgumentException("pipelineId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        DocumentSourceRequest source = new DocumentSourceRequest(
                "file",
                file.getOriginalFilename(),
                file.getOriginalFilename(),
                Map.of()
        );
        return executeInternal(
                pipelineId,
                parsePipelineId(pipelineId),
                "file",
                source,
                Map.of(),
                Map.of(),
                file,
                null
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
                        .orderByDesc("create_time")
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

    private IngestionTaskResult executeInternal(String baseCode,
                                                Long pipelineId,
                                                String normalizedSourceType,
                                                DocumentSourceRequest source,
                                                Map<String, Object> metadata,
                                                Map<String, Object> vectorSpaceId,
                                                MultipartFile file,
                                                String sourceFileUrl) {
        IngestionTaskEntity task = new IngestionTaskEntity();
        task.pipelineId = pipelineId;
        task.sourceType = blankToNull(normalizedSourceType);
        task.sourceLocation = blankToNull(source.location());
        task.sourceFileName = blankToNull(resolveFileName(source, file));
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
                        baseCode,
                        file,
                        KnowledgeIngestionMode.INGEST,
                        pipelineId,
                        String.valueOf(task.id),
                        normalizedSourceType,
                        source.location(),
                        resolveFileName(source, file),
                        sourceFileUrl
                )
        );

        persistResult(task, result, metadata, vectorSpaceId);
        return new IngestionTaskResult(
                task.id,
                task.pipelineId,
                task.status,
                task.chunkCount,
                task.errorMessage
        );
    }

    private MultipartFile resolveSourceFile(DocumentSourceRequest source, String normalizedType) {
        if ("file".equals(normalizedType)) {
            return readLocalFile(source);
        }
        if ("url".equals(normalizedType) || "feishu".equals(normalizedType)) {
            return downloadRemoteSource(source);
        }
        throw new IllegalArgumentException("暂不支持的来源类型: " + source.type());
    }

    private MultipartFile readLocalFile(DocumentSourceRequest source) {
        String location = source.location();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("source.location 不能为空");
        }
        try {
            Path path = location.startsWith("file:")
                    ? Path.of(URI.create(location))
                    : Path.of(location);
            byte[] bytes = Files.readAllBytes(path);
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : path.getFileName().toString();
            return new SimpleMultipartFile(fileName, fileName, "application/octet-stream", bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取本地文件失败: " + exception.getMessage(), exception);
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
                throw new IllegalArgumentException("下载远程资源失败，HTTP 状态码: " + response.statusCode());
            }
            String fileName = StringUtils.hasText(source.fileName()) ? source.fileName() : resolveFileNameFromUrl(source.location());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new SimpleMultipartFile(fileName, fileName, contentType, response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("下载远程资源失败: " + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("下载远程资源失败: " + exception.getMessage(), exception);
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

    private void persistResult(IngestionTaskEntity task,
                               KnowledgeIngestionResult result,
                               Map<String, Object> metadata,
                               Map<String, Object> vectorSpaceId) {
        boolean success = result.ingestionSummary() != null && result.ingestionSummary().success();
        task.status = success ? "SUCCESS" : "FAILED";
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
            throw new IllegalArgumentException("task id 不能为空");
        }
        IngestionTaskEntity entity = taskMapper.selectById(id);
        if (entity == null || entity.deleted != null && entity.deleted != 0) {
            throw new IllegalArgumentException("task 不存在");
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

    private Long parsePipelineId(String pipelineId) {
        if (!StringUtils.hasText(pipelineId)) {
            return null;
        }
        try {
            return Long.valueOf(pipelineId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return "file";
        }
        return sourceType.trim().toLowerCase(Locale.ROOT);
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
                                              Map<String, Object> vectorSpaceId) {
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
