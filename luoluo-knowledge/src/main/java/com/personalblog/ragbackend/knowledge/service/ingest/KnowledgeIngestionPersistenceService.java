package com.personalblog.ragbackend.knowledge.service.ingest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnBean(DataSource.class)
public class KnowledgeIngestionPersistenceService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionPersistenceService(KnowledgeProperties knowledgeProperties,
                                                KnowledgeBaseMapper knowledgeBaseMapper,
                                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                                KnowledgeChunkMapper knowledgeChunkMapper,
                                                KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                                ObjectMapper objectMapper) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persist(KnowledgeIngestionContext context) {
        if (!context.isIngestMode()) {
            return;
        }

        if (context.getPlan() == null || context.getParseResult() == null || context.getChunkResponse() == null) {
            return;
        }

        if (!context.getParseResult().success() || !context.getChunkResponse().success()) {
            return;
        }

        KnowledgeBaseEntity baseEntity = ensureKnowledgeBase(context);
        KnowledgeDocumentEntity documentEntity = upsertDocument(context, baseEntity);
        List<String> staleVectorIds = purgeDocumentArtifacts(documentEntity.getId());
        List<KnowledgeChunkEntity> chunkEntities = insertChunks(context, baseEntity.getId(), documentEntity.getId());

        context.setKnowledgeBaseId(baseEntity.getId());
        context.setDocumentId(documentEntity.getId());
        context.setPersistedChunks(chunkEntities);
        context.setStaleVectorIds(staleVectorIds);
    }

    public void saveVectorRefs(KnowledgeIngestionContext context) {
        if (context.getPlan() == null
                || context.getKnowledgeBaseId() == null
                || context.getDocumentId() == null
                || context.getPersistedChunks().isEmpty()) {
            return;
        }

        for (KnowledgeChunkEntity chunkEntity : context.getPersistedChunks()) {
            KnowledgeVectorRefEntity vectorRefEntity = new KnowledgeVectorRefEntity();
            vectorRefEntity.setKbId(context.getKnowledgeBaseId());
            vectorRefEntity.setDocId(context.getDocumentId());
            vectorRefEntity.setChunkId(chunkEntity.getId());
            vectorRefEntity.setCollectionName(context.getPlan().vectorSpace().collectionName());
            vectorRefEntity.setVectorId(String.valueOf(chunkEntity.getId()));
            vectorRefEntity.setEmbeddingModel(context.getPlan().vectorSpace().embeddingModel());
            vectorRefEntity.setEmbeddingDim(context.getPlan().vectorSpace().dimension());
            vectorRefEntity.setMetadata(buildVectorRefMetadata(chunkEntity));
            knowledgeVectorRefMapper.insert(vectorRefEntity);
        }
    }

    public void markDocumentIndexed(Long documentId, int chunkCount) {
        if (documentId == null) {
            return;
        }
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(documentId);
        entity.setStatus("success");
        entity.setChunkCount(chunkCount);
        knowledgeDocumentMapper.updateById(entity);
    }

    public void markDocumentFailed(Long documentId) {
        if (documentId == null) {
            return;
        }
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(documentId);
        entity.setStatus("failed");
        knowledgeDocumentMapper.updateById(entity);
    }

    private KnowledgeBaseEntity ensureKnowledgeBase(KnowledgeIngestionContext context) {
        String collectionName = context.getPlan().vectorSpace().collectionName();
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(
                Wrappers.<KnowledgeBaseEntity>lambdaQuery()
                        .eq(KnowledgeBaseEntity::getCollectionName, collectionName)
                        .last("limit 1")
        );
        if (existing != null) {
            return existing;
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(context.getPlan().baseCode());
        entity.setDescription("Auto managed knowledge base for " + context.getPlan().baseCode());
        entity.setEmbeddingModel(context.getPlan().vectorSpace().embeddingModel());
        entity.setCollectionName(collectionName);
        entity.setVisibility("PRIVATE");
        entity.setStatus("ACTIVE");
        knowledgeBaseMapper.insert(entity);
        return entity;
    }

    private KnowledgeDocumentEntity upsertDocument(KnowledgeIngestionContext context, KnowledgeBaseEntity baseEntity) {
        MultipartFile file = context.getFile();
        String docName = resolveDocumentName(file);
        String contentHash = sha256Hex(context.getParseResult().content());

        KnowledgeDocumentEntity existing = knowledgeDocumentMapper.selectOne(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getKbId, baseEntity.getId())
                        .eq(KnowledgeDocumentEntity::getDocName, docName)
                        .orderByDesc(KnowledgeDocumentEntity::getId)
                        .last("limit 1")
        );

        if (existing == null) {
            KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
            fillDocumentEntity(entity, baseEntity.getId(), docName, file, contentHash);
            knowledgeDocumentMapper.insert(entity);
            return entity;
        }

        fillDocumentEntity(existing, baseEntity.getId(), docName, file, contentHash);
        knowledgeDocumentMapper.updateById(existing);
        return existing;
    }

    private List<String> purgeDocumentArtifacts(Long documentId) {
        if (documentId == null) {
            return List.of();
        }

        List<KnowledgeVectorRefEntity> vectorRefs = knowledgeVectorRefMapper.selectList(
                Wrappers.<KnowledgeVectorRefEntity>lambdaQuery()
                        .eq(KnowledgeVectorRefEntity::getDocId, documentId)
        );
        knowledgeVectorRefMapper.delete(
                Wrappers.<KnowledgeVectorRefEntity>lambdaQuery()
                        .eq(KnowledgeVectorRefEntity::getDocId, documentId)
        );
        knowledgeChunkMapper.delete(
                Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
        );
        return vectorRefs.stream()
                .map(KnowledgeVectorRefEntity::getVectorId)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<KnowledgeChunkEntity> insertChunks(KnowledgeIngestionContext context, Long kbId, Long documentId) {
        DocumentChunkResponse chunkResponse = context.getChunkResponse();
        List<KnowledgeChunkEntity> entities = new ArrayList<>(chunkResponse.chunks().size());
        for (DocumentChunk chunk : chunkResponse.chunks()) {
            KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
            entity.setKbId(kbId);
            entity.setDocId(documentId);
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setContent(chunk.content());
            entity.setContentHash(sha256Hex(chunk.content()));
            entity.setCharCount(chunk.contentLength());
            entity.setTokenCount(estimateTokenCount(chunk.content()));
            entity.setEnabled(1);
            entity.setMetadata(buildChunkMetadata(chunk, context));
            knowledgeChunkMapper.insert(entity);
            entities.add(entity);
        }
        return entities;
    }

    private void fillDocumentEntity(KnowledgeDocumentEntity entity,
                                    Long kbId,
                                    String docName,
                                    MultipartFile file,
                                    String contentHash) {
        entity.setKbId(kbId);
        entity.setDocName(docName);
        entity.setEnabled(1);
        entity.setChunkCount(0);
        entity.setFileUrl(null);
        entity.setFileType(file == null ? null : file.getContentType());
        entity.setFileSize(file == null ? null : file.getSize());
        entity.setProcessMode("pipeline");
        entity.setStatus("running");
        entity.setSourceType("file");
        entity.setSourceLocation(docName);
        entity.setContentHash(contentHash);
        entity.setScheduleEnabled(0);
        entity.setScheduleCron(null);
        entity.setChunkStrategy(knowledgeProperties.getChunking().getStrategy());
        entity.setChunkConfig(buildChunkConfigJson());
        entity.setPipelineId(null);
    }

    private String buildChunkMetadata(DocumentChunk chunk, KnowledgeIngestionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sectionTitle", chunk.sectionTitle());
        metadata.put("contentLength", chunk.contentLength());
        metadata.put("overlapFromPrevious", chunk.overlapFromPrevious());
        if (context.getParseResult() != null && context.getParseResult().metadata() != null) {
            metadata.put("documentMetadata", context.getParseResult().metadata());
        }
        return writeJson(metadata);
    }

    private String buildVectorRefMetadata(KnowledgeChunkEntity chunkEntity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkIndex", chunkEntity.getChunkIndex());
        metadata.put("docId", chunkEntity.getDocId());
        return writeJson(metadata);
    }

    private String buildChunkConfigJson() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("strategy", knowledgeProperties.getChunking().getStrategy());
        config.put("chunkSize", knowledgeProperties.getChunking().getChunkSize());
        config.put("chunkOverlap", knowledgeProperties.getChunking().getChunkOverlap());
        config.put("maxChunkCount", knowledgeProperties.getChunking().getMaxChunkCount());
        return writeJson(config);
    }

    private String resolveDocumentName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            return "uploaded-document";
        }
        return file.getOriginalFilename().trim();
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private String writeJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize knowledge metadata", exception);
        }
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
