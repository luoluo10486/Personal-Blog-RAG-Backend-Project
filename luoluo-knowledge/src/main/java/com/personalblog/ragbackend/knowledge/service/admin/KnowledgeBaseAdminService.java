package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dto.admin.ChunkStrategyOption;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseView;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseAdminService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeAdminSupport support;
    private final ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider;

    public KnowledgeBaseAdminService(KnowledgeProperties knowledgeProperties,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeAdminSupport support,
                                     ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.support = support;
        this.vectorStoreAdminProvider = vectorStoreAdminProvider;
    }

    @Transactional
    public Long create(KnowledgeBaseCreateRequest request) {
        String name = requireText(request.getName(), "知识库名称不能为空");
        String collectionName = normalizeCollectionName(request.getCollectionName(), name);
        assertCollectionAvailable(collectionName, null);
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(name);
        entity.setDescription(blankToNull(request.getDescription()));
        entity.setEmbeddingModel(resolveEmbeddingModel(request.getEmbeddingModel()));
        entity.setCollectionName(collectionName);
        entity.setVisibility(defaultIfBlank(request.getVisibility(), "PRIVATE"));
        entity.setStatus(defaultIfBlank(request.getStatus(), "ACTIVE"));
        knowledgeBaseMapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(Long kbId, KnowledgeBaseUpdateRequest request) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(kbId);
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(blankToNull(request.getDescription()));
        }
        if (StringUtils.hasText(request.getEmbeddingModel())) {
            entity.setEmbeddingModel(request.getEmbeddingModel().trim());
        }
        if (StringUtils.hasText(request.getVisibility())) {
            entity.setVisibility(request.getVisibility().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            entity.setStatus(request.getStatus().trim());
        }
        knowledgeBaseMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long kbId) {
        requireKnowledgeBase(kbId);
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, kbId));
        if (documentCount > 0) {
            throw new IllegalArgumentException("知识库下仍有文档，不能直接删除");
        }
        knowledgeBaseMapper.deleteById(kbId);
    }

    public KnowledgeBaseView get(Long kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(kbId);
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, kbId));
        return support.toKnowledgeBaseView(entity, documentCount);
    }

    public IPage<KnowledgeBaseView> page(KnowledgeBasePageRequest request) {
        IPage<KnowledgeBaseEntity> entityPage = knowledgeBaseMapper.selectPage(
                support.newPage(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .like(StringUtils.hasText(request.getName()), KnowledgeBaseEntity::getName, request.getName())
                        .eq(StringUtils.hasText(request.getStatus()), KnowledgeBaseEntity::getStatus, request.getStatus())
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
        List<KnowledgeBaseView> records = entityPage.getRecords().stream()
                .map(entity -> support.toKnowledgeBaseView(entity, documentCountByKb.getOrDefault(entity.getId(), 0L)))
                .toList();
        return support.mapPage(entityPage, records);
    }

    public List<ChunkStrategyOption> listChunkStrategies() {
        return support.chunkStrategyOptions();
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
            throw new IllegalArgumentException("向量集合名称已存在");
        }
        VectorStoreAdmin vectorStoreAdmin = vectorStoreAdminProvider.getIfAvailable();
        if (vectorStoreAdmin != null
                && vectorStoreAdmin.vectorSpaceExists(null, collectionName)
                && existing == null) {
            throw new IllegalArgumentException("向量集合已被占用");
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
