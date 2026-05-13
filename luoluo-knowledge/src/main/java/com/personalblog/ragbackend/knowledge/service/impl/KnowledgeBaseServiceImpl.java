package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeBaseService;
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
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider;

    public KnowledgeBaseServiceImpl(KnowledgeProperties knowledgeProperties,
                                    KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    KnowledgeFileStorageService knowledgeFileStorageService,
                                    ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.vectorStoreAdminProvider = vectorStoreAdminProvider;
    }

    @Override
    @Transactional
    public String create(KnowledgeBaseCreateRequest requestParam) {
        String name = requireText(requestParam.getName(), "knowledge base name is required");
        String collectionName = StringUtils.hasText(requestParam.getCollectionName())
                ? requestParam.getCollectionName().trim()
                : knowledgeProperties.getDefaults().getCollectionName();
        assertCollectionAvailable(collectionName, null);

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(name);
        entity.setEmbeddingModel(StringUtils.hasText(requestParam.getEmbeddingModel())
                ? requestParam.getEmbeddingModel().trim()
                : knowledgeProperties.getDefaults().getEmbeddingModel());
        entity.setCollectionName(collectionName);
        entity.setCreatedBy(null);
        entity.setUpdatedBy(null);
        entity.setDeleted(0);
        knowledgeBaseMapper.insert(entity);
        knowledgeFileStorageService.ensureBucketExists(knowledgeFileStorageService.resolveBucketName(collectionName));
        return String.valueOf(entity.getId());
    }

    @Override
    @Transactional
    public void update(KnowledgeBaseUpdateRequest requestParam) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(requestParam.getId()));
        if (StringUtils.hasText(requestParam.getName())) {
            entity.setName(requestParam.getName().trim());
        }
        if (StringUtils.hasText(requestParam.getEmbeddingModel())) {
            entity.setEmbeddingModel(requestParam.getEmbeddingModel().trim());
        }
        entity.setUpdatedBy(null);
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void rename(String kbId, KnowledgeBaseUpdateRequest requestParam) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        if (!StringUtils.hasText(requestParam.getName())) {
            throw new IllegalArgumentException("knowledge base name is required");
        }
        entity.setName(requestParam.getName().trim());
        entity.setUpdatedBy(null);
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(String kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, entity.getId()));
        if (documentCount > 0) {
            throw new IllegalArgumentException("knowledge base still has documents");
        }
        knowledgeBaseMapper.deleteById(entity.getId());
    }

    @Override
    public KnowledgeBaseVO queryById(String kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setCollectionName(entity.getCollectionName());
        vo.setDocumentCount(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, entity.getId())));
        vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        vo.setCreateTime(entity.getCreatedAt() == null ? null : java.util.Date.from(entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        vo.setUpdateTime(entity.getUpdatedAt() == null ? null : java.util.Date.from(entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        return vo;
    }

    @Override
    public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam) {
        Page<KnowledgeBaseEntity> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeBaseEntity> result = knowledgeBaseMapper.selectPage(
                page,
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .like(StringUtils.hasText(requestParam.getName()), KnowledgeBaseEntity::getName, requestParam.getName())
                        .orderByDesc(KnowledgeBaseEntity::getUpdatedAt)
        );
        List<Long> kbIds = result.getRecords().stream().map(KnowledgeBaseEntity::getId).toList();
        Map<Long, Long> documentCountMap = kbIds.isEmpty()
                ? Map.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .in(KnowledgeDocumentEntity::getKbId, kbIds))
                .stream()
                .collect(Collectors.groupingBy(KnowledgeDocumentEntity::getKbId, Collectors.counting()));
        return result.convert(entity -> {
            KnowledgeBaseVO vo = new KnowledgeBaseVO();
            vo.setId(String.valueOf(entity.getId()));
            vo.setName(entity.getName());
            vo.setEmbeddingModel(entity.getEmbeddingModel());
            vo.setCollectionName(entity.getCollectionName());
            vo.setDocumentCount(documentCountMap.getOrDefault(entity.getId(), 0L));
            vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
            vo.setCreateTime(entity.getCreatedAt() == null ? null : java.util.Date.from(entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            vo.setUpdateTime(entity.getUpdatedAt() == null ? null : java.util.Date.from(entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            return vo;
        });
    }

    @Override
    public List<ChunkStrategyVO> listChunkStrategies() {
        return List.of(
                new ChunkStrategyVO("structure-aware", "Structure Aware", java.util.Map.of("strategy", 1)),
                new ChunkStrategyVO("fixed-size", "Fixed Size", java.util.Map.of("strategy", 1))
        );
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("knowledge base not found");
        }
        return entity;
    }

    private Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
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
}
