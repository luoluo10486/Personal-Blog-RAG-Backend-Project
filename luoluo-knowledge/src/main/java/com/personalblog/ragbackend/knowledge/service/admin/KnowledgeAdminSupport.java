package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.controller.vo.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeAdminSupport {
    private static final String DEFAULT_COLLECTION_PREFIX = "kb_";
    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 128;
    private final RAGDefaultProperties ragDefaultProperties;

    public KnowledgeAdminSupport(RAGDefaultProperties ragDefaultProperties) {
        this.ragDefaultProperties = ragDefaultProperties;
    }

    public <T> Page<T> newPage(long current, long size) {
        return new Page<>(Math.max(current, 1), Math.max(size, 1));
    }

    public <T, R> Page<R> mapPage(IPage<T> source, List<R> records) {
        Page<R> page = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        page.setRecords(records);
        return page;
    }

    public KnowledgeBaseVO toKnowledgeBaseView(KnowledgeBaseEntity entity, long documentCount) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setCollectionName(entity.getCollectionName());
        vo.setDocumentCount(documentCount);
        vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        vo.setCreateTime(entity.getCreatedAt() == null ? null : Date.from(entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        vo.setUpdateTime(entity.getUpdatedAt() == null ? null : Date.from(entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        return vo;
    }

    public KnowledgeDocumentVO toKnowledgeDocumentView(KnowledgeDocumentEntity entity) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setKbId(String.valueOf(entity.getKbId()));
        vo.setDocName(entity.getDocName());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceLocation(entity.getSourceLocation());
        vo.setScheduleEnabled(entity.getScheduleEnabled());
        vo.setScheduleCron(entity.getScheduleCron());
        vo.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        vo.setChunkCount(entity.getChunkCount());
        vo.setFileUrl(entity.getFileUrl());
        vo.setFileType(entity.getFileType());
        vo.setFileSize(entity.getFileSize());
        vo.setChunkStrategy(entity.getChunkStrategy());
        vo.setProcessMode(entity.getProcessMode());
        vo.setChunkConfig(entity.getChunkConfig());
        vo.setPipelineId(entity.getPipelineId() == null ? null : String.valueOf(entity.getPipelineId()));
        vo.setStatus(entity.getStatus());
        vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        vo.setUpdatedBy(entity.getUpdatedBy() == null ? null : String.valueOf(entity.getUpdatedBy()));
        vo.setCreateTime(entity.getCreatedAt());
        vo.setUpdateTime(entity.getUpdatedAt());
        return vo;
    }

    public KnowledgeDocumentSearchVO toKnowledgeDocumentSearchView(KnowledgeDocumentEntity entity) {
        KnowledgeDocumentSearchVO vo = new KnowledgeDocumentSearchVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setKbId(String.valueOf(entity.getKbId()));
        vo.setDocName(entity.getDocName());
        vo.setKbName(null);
        return vo;
    }

    public KnowledgeDocumentChunkLogVO toKnowledgeDocumentChunkLogView(KnowledgeDocumentChunkLogEntity entity) {
        KnowledgeDocumentChunkLogVO vo = new KnowledgeDocumentChunkLogVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setDocId(String.valueOf(entity.getDocId()));
        vo.setStatus(entity.getStatus());
        vo.setProcessMode(entity.getProcessMode());
        vo.setChunkStrategy(entity.getChunkStrategy());
        vo.setPipelineId(entity.getPipelineId() == null ? null : String.valueOf(entity.getPipelineId()));
        vo.setPipelineName(null);
        vo.setExtractDuration(entity.getExtractDuration());
        vo.setChunkDuration(entity.getChunkDuration());
        vo.setEmbedDuration(entity.getEmbedDuration());
        vo.setPersistDuration(entity.getPersistDuration());
        vo.setOtherDuration(null);
        vo.setTotalDuration(entity.getTotalDuration());
        vo.setChunkCount(entity.getChunkCount());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartTime(toDate(entity.getStartedAt()));
        vo.setEndTime(toDate(entity.getEndedAt()));
        vo.setCreateTime(toDate(entity.getCreatedAt()));
        return vo;
    }

    public KnowledgeChunkVO toKnowledgeChunkView(KnowledgeChunkEntity entity) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setKbId(String.valueOf(entity.getKbId()));
        vo.setDocId(String.valueOf(entity.getDocId()));
        vo.setChunkIndex(entity.getChunkIndex());
        vo.setContent(entity.getContent());
        vo.setContentHash(entity.getContentHash());
        vo.setCharCount(entity.getCharCount());
        vo.setTokenCount(entity.getTokenCount());
        vo.setEnabled(entity.getEnabled());
        vo.setCreateTime(entity.getCreatedAt());
        vo.setUpdateTime(entity.getUpdatedAt());
        return vo;
    }

    private Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    public List<ChunkStrategyVO> chunkStrategyOptions() {
        return List.of(
                new ChunkStrategyVO(
                        ChunkingMode.STRUCTURE_AWARE.getValue(),
                        ChunkingMode.STRUCTURE_AWARE.getLabel(),
                        ChunkingMode.STRUCTURE_AWARE.getDefaultConfig()
                ),
                new ChunkStrategyVO(
                        ChunkingMode.FIXED_SIZE.getValue(),
                        ChunkingMode.FIXED_SIZE.getLabel(),
                        ChunkingMode.FIXED_SIZE.getDefaultConfig()
                )
        );
    }

    public String defaultCollectionName(String name) {
        String normalized = normalizeCode(name);
        if (!StringUtils.hasText(normalized)
                || normalized.equals(ragDefaultProperties.getCollectionName())) {
            return ragDefaultProperties.getCollectionName();
        }
        return DEFAULT_COLLECTION_PREFIX + normalized;
    }

    public String defaultChunkConfig(String strategy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        ChunkingMode mode = ChunkingMode.from(strategy);
        payload.put("strategy", mode.getValue());
        if (mode == ChunkingMode.FIXED_SIZE) {
            payload.put("chunkSize", DEFAULT_CHUNK_SIZE);
            payload.put("overlapSize", DEFAULT_CHUNK_OVERLAP);
        } else {
            payload.put("targetChars", 1400);
            payload.put("overlapChars", 0);
            payload.put("maxChars", 1800);
            payload.put("minChars", 600);
        }
        return toJson(payload);
    }

    public String normalizeChunkStrategy(String strategy) {
        return ChunkingMode.from(strategy).getValue();
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
