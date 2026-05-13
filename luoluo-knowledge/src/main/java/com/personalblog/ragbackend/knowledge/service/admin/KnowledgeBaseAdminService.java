package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseAdminService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeAdminSupport support;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider;

    public KnowledgeBaseAdminService(KnowledgeProperties knowledgeProperties,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeAdminSupport support,
                                     KnowledgeFileStorageService knowledgeFileStorageService,
                                     ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.support = support;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.vectorStoreAdminProvider = vectorStoreAdminProvider;
    }

    @Transactional
    public Long create(KnowledgeBaseCreateRequest request) {
        String name = requireText(request.getName(), "knowledge base name is required");
        String collectionName = normalizeCollectionName(request.getCollectionName(), name);
        assertCollectionAvailable(collectionName, null);
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(name);
        entity.setEmbeddingModel(resolveEmbeddingModel(request.getEmbeddingModel()));
        entity.setCollectionName(collectionName);
        knowledgeBaseMapper.insert(entity);
        knowledgeFileStorageService.ensureBucketExists(
                knowledgeFileStorageService.resolveBucketName(collectionName)
        );
        return entity.getId();
    }

    @Transactional
    public void update(Long kbId, KnowledgeBaseUpdateRequest request) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(kbId);
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getEmbeddingModel())) {
            entity.setEmbeddingModel(request.getEmbeddingModel().trim());
        }
        knowledgeBaseMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long kbId) {
        requireKnowledgeBase(kbId);
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, kbId));
        if (documentCount > 0) {
            throw new IllegalArgumentException("knowledge base still has documents");
        }
        knowledgeBaseMapper.deleteById(kbId);
    }

    public KnowledgeBaseVO get(Long kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(kbId);
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, kbId));
        return support.toKnowledgeBaseView(entity, documentCount);
    }

    public IPage<KnowledgeBaseVO> page(KnowledgeBasePageRequest request) {
        IPage<KnowledgeBaseEntity> entityPage = knowledgeBaseMapper.selectPage(
                support.newPage(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .like(StringUtils.hasText(request.getName()), KnowledgeBaseEntity::getName, request.getName())
                        .orderByDesc(KnowledgeBaseEntity::getUpdatedAt)
        );
        List<Long> kbIds = entityPage.getRecords().stream()
                .map(KnowledgeBaseEntity::getId)
                .toList();
        Map<Long, Long> documentCountByKb = kbIds.isEmpty()
                ? Map.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .in(KnowledgeDocumentEntity::getKbId, kbIds))
                .stream()
                .collect(Collectors.groupingBy(KnowledgeDocumentEntity::getKbId, Collectors.counting()));
        List<KnowledgeBaseVO> records = entityPage.getRecords().stream()
                .map(entity -> support.toKnowledgeBaseView(entity, documentCountByKb.getOrDefault(entity.getId(), 0L)))
                .toList();
        return support.mapPage(entityPage, records);
    }

    public List<ChunkStrategyVO> listChunkStrategies() {
        return support.chunkStrategyOptions();
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("knowledge base id is required");
        }
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("knowledge base not found");
        }
        return entity;
    }

    private String normalizeCollectionName(String collectionName, String name) {
        if (StringUtils.hasText(collectionName)) {
            return collectionName.trim();
        }
        return support.defaultCollectionName(name);
    }

    private void assertCollectionAvailable(String collectionName, Long currentKbId) {
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getCollectionName, collectionName)
                .last("limit 1"));
        if (existing != null && !existing.getId().equals(currentKbId)) {
            throw new IllegalArgumentException("collection name already exists");
        }
        VectorStoreAdmin vectorStoreAdmin = vectorStoreAdminProvider.getIfAvailable();
        if (vectorStoreAdmin != null
                && vectorStoreAdmin.vectorSpaceExists(null, collectionName)
                && existing == null) {
            throw new IllegalArgumentException("collection already occupied");
        }
    }

    private String resolveEmbeddingModel(String embeddingModel) {
        if (StringUtils.hasText(embeddingModel)) {
            return embeddingModel.trim();
        }
        return knowledgeProperties.getDefaults().getEmbeddingModel();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
