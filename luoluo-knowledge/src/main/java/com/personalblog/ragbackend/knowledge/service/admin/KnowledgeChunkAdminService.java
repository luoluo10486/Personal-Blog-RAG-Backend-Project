package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.infra.ai.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkView;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeChunkAdminService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeAdminSupport support;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;

    public KnowledgeChunkAdminService(KnowledgeBaseMapper knowledgeBaseMapper,
                                      KnowledgeDocumentMapper knowledgeDocumentMapper,
                                      KnowledgeChunkMapper knowledgeChunkMapper,
                                      KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                      KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                      KnowledgeAdminSupport support,
                                      ObjectProvider<EmbeddingService> embeddingServiceProvider,
                                      ObjectProvider<VectorStoreService> vectorStoreServiceProvider) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.support = support;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
    }

    public IPage<KnowledgeChunkView> page(Long documentId, KnowledgeChunkPageRequest request) {
        requireDocument(documentId);
        IPage<KnowledgeChunkEntity> page = knowledgeChunkMapper.selectPage(
                support.newPage(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
                        .eq(request.getEnabled() != null, KnowledgeChunkEntity::getEnabled, request.getEnabled())
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
                        .orderByAsc(KnowledgeChunkEntity::getId)
        );
        return support.mapPage(page, page.getRecords().stream().map(support::toKnowledgeChunkView).toList());
    }

    @Transactional
    public KnowledgeChunkView create(Long documentId, KnowledgeChunkCreateRequest request) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        String content = requireContent(request.getContent());
        int chunkIndex = resolveChunkIndex(documentId, request.getIndex());
        shiftChunkIndexes(documentId, chunkIndex, 1);
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setKbId(document.getKbId());
        entity.setDocId(documentId);
        entity.setChunkIndex(chunkIndex);
        entity.setContent(content);
        entity.setContentHash(support.sha256Hex(content));
        entity.setCharCount(content.length());
        entity.setTokenCount(support.estimateTokenCount(content));
        entity.setEnabled(1);
        entity.setMetadata("{}");
        knowledgeChunkMapper.insert(entity);
        updateDocumentChunkCount(documentId);
        syncChunkVector(document, entity);
        return support.toKnowledgeChunkView(requireChunk(documentId, entity.getId()));
    }

    @Transactional
    public void update(Long documentId, Long chunkId, KnowledgeChunkUpdateRequest request) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        KnowledgeChunkEntity entity = requireChunk(documentId, chunkId);
        String content = requireContent(request.getContent());
        entity.setContent(content);
        entity.setContentHash(support.sha256Hex(content));
        entity.setCharCount(content.length());
        entity.setTokenCount(support.estimateTokenCount(content));
        knowledgeChunkMapper.updateById(entity);
        syncChunkVector(document, entity);
    }

    @Transactional
    public void delete(Long documentId, Long chunkId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        KnowledgeChunkEntity entity = requireChunk(documentId, chunkId);
        deleteChunkVector(document, entity.getId());
        int removedIndex = entity.getChunkIndex() == null ? Integer.MAX_VALUE : entity.getChunkIndex();
        knowledgeChunkMapper.deleteById(chunkId);
        shiftChunkIndexes(documentId, removedIndex + 1, -1);
        updateDocumentChunkCount(documentId);
    }

    @Transactional
    public void enableChunk(Long documentId, Long chunkId, boolean enabled) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        KnowledgeChunkEntity entity = requireChunk(documentId, chunkId);
        entity.setEnabled(enabled ? 1 : 0);
        knowledgeChunkMapper.updateById(entity);
        if (enabled) {
            syncChunkVector(document, entity);
        } else {
            deleteChunkVector(document, entity.getId());
        }
    }

    @Transactional
    public void batchToggleEnabled(Long documentId, KnowledgeChunkBatchRequest request, boolean enabled) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        List<KnowledgeChunkEntity> chunks = loadTargetChunks(documentId, request);
        for (KnowledgeChunkEntity chunk : chunks) {
            chunk.setEnabled(enabled ? 1 : 0);
            knowledgeChunkMapper.updateById(chunk);
            if (enabled) {
                syncChunkVector(document, chunk);
            } else {
                deleteChunkVector(document, chunk.getId());
            }
        }
    }

    private List<KnowledgeChunkEntity> loadTargetChunks(Long documentId, KnowledgeChunkBatchRequest request) {
        List<Long> chunkIds = request == null ? null : request.getChunkIds();
        if (chunkIds == null || chunkIds.isEmpty()) {
            return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getDocId, documentId));
        }
        Set<Long> idSet = chunkIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId)
                .in(KnowledgeChunkEntity::getId, idSet));
    }

    private void syncChunkVector(KnowledgeDocumentEntity document, KnowledgeChunkEntity chunk) {
        if (!isVectorSyncEnabled(document, chunk)) {
            return;
        }
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        if (embeddingService == null || vectorStoreService == null) {
            return;
        }

        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKbId());
        String baseCode = resolveBaseCode(knowledgeBase);
        KnowledgeVectorSpace vectorSpace = vectorSpaceResolver.resolve(baseCode);
        List<Float> embedding = embeddingService.embed(chunk.getContent());
        vectorStoreService.upsert(vectorSpace, List.of(new KnowledgeVectorDocument(
                String.valueOf(chunk.getId()),
                chunk.getContent(),
                embedding,
                buildVectorMetadata(baseCode, document, chunk)
        )));
        upsertVectorRef(document, knowledgeBase, vectorSpace, chunk, embedding.size());
    }

    private boolean isVectorSyncEnabled(KnowledgeDocumentEntity document, KnowledgeChunkEntity chunk) {
        return document.getEnabled() != null
                && document.getEnabled() == 1
                && chunk.getEnabled() != null
                && chunk.getEnabled() == 1;
    }

    private void upsertVectorRef(KnowledgeDocumentEntity document,
                                 KnowledgeBaseEntity knowledgeBase,
                                 KnowledgeVectorSpace vectorSpace,
                                 KnowledgeChunkEntity chunk,
                                 int dimension) {
        KnowledgeVectorRefEntity entity = knowledgeVectorRefMapper.selectOne(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getChunkId, chunk.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new KnowledgeVectorRefEntity();
            entity.setChunkId(chunk.getId());
        }
        entity.setKbId(knowledgeBase.getId());
        entity.setDocId(document.getId());
        entity.setCollectionName(vectorSpace.collectionName());
        entity.setVectorId(String.valueOf(chunk.getId()));
        entity.setEmbeddingModel(vectorSpace.embeddingModel());
        entity.setEmbeddingDim(dimension);
        entity.setMetadata("{\"chunkIndex\":" + chunk.getChunkIndex() + "}");
        if (entity.getId() == null) {
            knowledgeVectorRefMapper.insert(entity);
        } else {
            knowledgeVectorRefMapper.updateById(entity);
        }
    }

    private void deleteChunkVector(KnowledgeDocumentEntity document, Long chunkId) {
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        KnowledgeVectorRefEntity vectorRef = knowledgeVectorRefMapper.selectOne(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getChunkId, chunkId)
                .last("limit 1"));
        if (vectorRef != null && vectorStoreService != null && StringUtils.hasText(vectorRef.getVectorId())) {
            String baseCode = resolveBaseCode(requireKnowledgeBase(document.getKbId()));
            vectorStoreService.deleteByIds(vectorSpaceResolver.resolve(baseCode), List.of(vectorRef.getVectorId()));
        }
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getChunkId, chunkId));
    }

    private Map<String, Object> buildVectorMetadata(String baseCode,
                                                    KnowledgeDocumentEntity document,
                                                    KnowledgeChunkEntity chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkId", String.valueOf(chunk.getId()));
        metadata.put("documentId", String.valueOf(document.getId()));
        metadata.put("knowledgeBaseId", String.valueOf(document.getKbId()));
        metadata.put("baseCode", baseCode);
        metadata.put("title", document.getDocName() == null ? "" : document.getDocName());
        metadata.put("sourceUrl", document.getFileUrl() == null ? "" : document.getFileUrl());
        metadata.put("chunkIndex", chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex());
        return metadata;
    }

    private void updateDocumentChunkCount(Long documentId) {
        long chunkCount = knowledgeChunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId));
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(documentId);
        entity.setChunkCount((int) chunkCount);
        knowledgeDocumentMapper.updateById(entity);
    }

    private void shiftChunkIndexes(Long documentId, int startIndex, int delta) {
        if (delta == 0) {
            return;
        }
        List<KnowledgeChunkEntity> affected = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId)
                .ge(KnowledgeChunkEntity::getChunkIndex, startIndex)
                .orderByDesc(delta > 0, KnowledgeChunkEntity::getChunkIndex)
                .orderByAsc(delta < 0, KnowledgeChunkEntity::getChunkIndex));
        for (KnowledgeChunkEntity chunk : affected) {
            chunk.setChunkIndex((chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex()) + delta);
            knowledgeChunkMapper.updateById(chunk);
        }
    }

    private int resolveChunkIndex(Long documentId, Integer requestedIndex) {
        if (requestedIndex != null && requestedIndex >= 0) {
            return requestedIndex;
        }
        KnowledgeChunkEntity lastChunk = knowledgeChunkMapper.selectOne(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId)
                .orderByDesc(KnowledgeChunkEntity::getChunkIndex)
                .last("limit 1"));
        return lastChunk == null || lastChunk.getChunkIndex() == null ? 0 : lastChunk.getChunkIndex() + 1;
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("知识库 ID 不能为空");
        }
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("知识库不存在");
        }
        return entity;
    }

    private KnowledgeDocumentEntity requireDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(documentId);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return entity;
    }

    private KnowledgeChunkEntity requireChunk(Long documentId, Long chunkId) {
        if (chunkId == null) {
            throw new IllegalArgumentException("Chunk ID 不能为空");
        }
        KnowledgeChunkEntity entity = knowledgeChunkMapper.selectById(chunkId);
        if (entity == null || !Objects.equals(entity.getDocId(), documentId)) {
            throw new IllegalArgumentException("Chunk 不存在");
        }
        return entity;
    }

    private String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Chunk 内容不能为空");
        }
        return content.trim();
    }

    private String resolveBaseCode(KnowledgeBaseEntity knowledgeBase) {
        String normalized = support.normalizeCode(knowledgeBase.getName());
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return String.valueOf(knowledgeBase.getId());
    }
}
