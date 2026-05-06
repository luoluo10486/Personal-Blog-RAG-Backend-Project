package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dto.admin.ChunkStrategyOption;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeAdminSupport {
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeAdminSupport(KnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    public <T> Page<T> newPage(long current, long size) {
        return new Page<>(Math.max(current, 1), Math.max(size, 1));
    }

    public <T, R> Page<R> mapPage(IPage<T> source, List<R> records) {
        Page<R> page = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        page.setRecords(records);
        return page;
    }

    public KnowledgeBaseView toKnowledgeBaseView(KnowledgeBaseEntity entity, long documentCount) {
        return new KnowledgeBaseView(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getEmbeddingModel(),
                entity.getCollectionName(),
                entity.getVisibility(),
                entity.getStatus(),
                documentCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public KnowledgeDocumentView toKnowledgeDocumentView(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentView(
                entity.getId(),
                entity.getKbId(),
                entity.getDocName(),
                entity.getSourceType(),
                entity.getSourceLocation(),
                entity.getScheduleEnabled(),
                entity.getScheduleCron(),
                entity.getEnabled(),
                entity.getChunkCount(),
                entity.getFileUrl(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getChunkStrategy(),
                entity.getProcessMode(),
                entity.getChunkConfig(),
                entity.getPipelineId(),
                entity.getStatus(),
                entity.getContentHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public KnowledgeDocumentSearchView toKnowledgeDocumentSearchView(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentSearchView(
                entity.getId(),
                entity.getKbId(),
                entity.getDocName(),
                entity.getStatus(),
                entity.getEnabled(),
                entity.getChunkCount()
        );
    }

    public KnowledgeChunkView toKnowledgeChunkView(KnowledgeChunkEntity entity) {
        return new KnowledgeChunkView(
                entity.getId(),
                entity.getKbId(),
                entity.getDocId(),
                entity.getChunkIndex(),
                entity.getContent(),
                entity.getContentHash(),
                entity.getCharCount(),
                entity.getTokenCount(),
                entity.getEnabled(),
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public List<ChunkStrategyOption> chunkStrategyOptions() {
        return List.of(
                new ChunkStrategyOption(
                        ChunkingMode.STRUCTURE_AWARE.code(),
                        "Structure Aware",
                        defaultChunkConfig(ChunkingMode.STRUCTURE_AWARE.code())
                ),
                new ChunkStrategyOption(
                        ChunkingMode.FIXED_SIZE.code(),
                        "Fixed Size",
                        defaultChunkConfig(ChunkingMode.FIXED_SIZE.code())
                )
        );
    }

    public String defaultCollectionName(String name) {
        String normalized = normalizeCode(name);
        if (!StringUtils.hasText(normalized)
                || normalized.equals(knowledgeProperties.getDefaultBaseCode())) {
            return knowledgeProperties.getDefaults().getCollectionName();
        }
        return knowledgeProperties.getVector().getMilvus().getCollectionPrefix() + normalized;
    }

    public String defaultChunkConfig(String strategy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategy", normalizeChunkStrategy(strategy));
        payload.put("chunkSize", knowledgeProperties.getChunking().getChunkSize());
        payload.put("chunkOverlap", knowledgeProperties.getChunking().getChunkOverlap());
        payload.put("maxChunkCount", knowledgeProperties.getChunking().getMaxChunkCount());
        return toJson(payload);
    }

    public String normalizeChunkStrategy(String strategy) {
        return ChunkingMode.from(strategy).code();
    }

    public String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    public String defaultDocumentStatus() {
        return "success";
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    public String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(result.length * 2);
            for (byte value : result) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }

    public int estimateTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return (int) List.of(content.trim().split("\\s+")).stream()
                .filter(StringUtils::hasText)
                .count();
    }

    private String toJson(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + jsonValue(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + text + "\"";
    }
}
