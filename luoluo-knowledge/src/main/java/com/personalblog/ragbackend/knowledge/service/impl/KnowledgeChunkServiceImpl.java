package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;

    public KnowledgeChunkServiceImpl(KnowledgeChunkMapper knowledgeChunkMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                     ObjectProvider<VectorStoreService> vectorStoreServiceProvider) {
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
    }

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        Long documentId = parseId(docId);
        requireDocument(documentId);
        IPage<KnowledgeChunkEntity> page = knowledgeChunkMapper.selectPage(
                new Page<>(requestParam.getCurrent(), requestParam.getSize()),
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
                        .eq(requestParam.getEnabled() != null, KnowledgeChunkEntity::getEnabled, requestParam.getEnabled())
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
        );
        return page.convert(this::toView);
    }

    @Override
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam) {
        Long documentId = parseId(docId);
        KnowledgeDocumentEntity document = requireDocument(documentId);
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setKbId(document.getKbId());
        entity.setDocId(documentId);
        entity.setChunkIndex(requestParam.getIndex());
        entity.setContent(requestParam.getContent());
        entity.setContentHash(requestParam.getContent());
        entity.setCharCount(requestParam.getContent() == null ? 0 : requestParam.getContent().length());
        entity.setTokenCount(requestParam.getContent() == null ? 0 : requestParam.getContent().split("\\s+").length);
        entity.setEnabled(1);
        knowledgeChunkMapper.insert(entity);
        return toView(entity);
    }

    @Override
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams) {
        batchCreate(docId, requestParams, false);
    }

    @Override
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams, boolean writeVector) {
        Long documentId = parseId(docId);
        KnowledgeDocumentEntity document = requireDocument(documentId);
        for (KnowledgeChunkCreateRequest request : requestParams) {
            create(docId, request);
        }
        if (writeVector) {
            List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getDocId, documentId));
            VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
            if (vectorStoreService != null) {
                KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKbId());
                vectorStoreService.upsert(
                        vectorSpaceResolver.resolve(String.valueOf(knowledgeBase.getId())),
                        chunks.stream()
                                .map(chunk -> new KnowledgeVectorDocument(String.valueOf(chunk.getId()), chunk.getContent(), List.of(), java.util.Map.of()))
                                .toList()
                );
            }
        }
    }

    @Override
    public void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam) {
        KnowledgeChunkEntity entity = requireChunk(parseId(docId), parseId(chunkId));
        entity.setContent(requestParam.getContent());
        knowledgeChunkMapper.updateById(entity);
    }

    @Override
    public void delete(String docId, String chunkId) {
        requireChunk(parseId(docId), parseId(chunkId));
        knowledgeChunkMapper.deleteById(parseId(chunkId));
    }

    @Override
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        KnowledgeChunkEntity entity = requireChunk(parseId(docId), parseId(chunkId));
        entity.setEnabled(enabled ? 1 : 0);
        knowledgeChunkMapper.updateById(entity);
    }

    @Override
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        Long documentId = parseId(docId);
        List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId)
                .in(requestParam.getChunkIds() != null && !requestParam.getChunkIds().isEmpty(),
                        KnowledgeChunkEntity::getId,
                        requestParam.getChunkIds().stream().map(this::parseId).toList()));
        for (KnowledgeChunkEntity chunk : chunks) {
            chunk.setEnabled(enabled ? 1 : 0);
            knowledgeChunkMapper.updateById(chunk);
        }
    }

    @Override
    public void updateEnabledByDocId(String docId, String kbId, boolean enabled) {
        Long documentId = parseId(docId);
        List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId));
        for (KnowledgeChunkEntity chunk : chunks) {
            chunk.setEnabled(enabled ? 1 : 0);
            knowledgeChunkMapper.updateById(chunk);
        }
    }

    @Override
    public List<KnowledgeChunkVO> listByDocId(String docId) {
        Long documentId = parseId(docId);
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public void deleteByDocId(String docId) {
        Long documentId = parseId(docId);
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId));
    }

    private KnowledgeChunkVO toView(KnowledgeChunkEntity chunk) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(String.valueOf(chunk.getId()));
        vo.setKbId(String.valueOf(chunk.getKbId()));
        vo.setDocId(String.valueOf(chunk.getDocId()));
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setContentHash(chunk.getContentHash());
        vo.setCharCount(chunk.getCharCount());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setEnabled(chunk.getEnabled());
        vo.setCreateTime(chunk.getCreatedAt());
        vo.setUpdateTime(chunk.getUpdatedAt());
        return vo;
    }

    private KnowledgeChunkEntity requireChunk(Long documentId, Long chunkId) {
        KnowledgeChunkEntity entity = knowledgeChunkMapper.selectById(chunkId);
        if (entity == null || !Objects.equals(entity.getDocId(), documentId)) {
            throw new IllegalArgumentException("chunk not found");
        }
        return entity;
    }

    private KnowledgeDocumentEntity requireDocument(Long documentId) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(documentId);
        if (entity == null) {
            throw new IllegalArgumentException("document not found");
        }
        return entity;
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
}
