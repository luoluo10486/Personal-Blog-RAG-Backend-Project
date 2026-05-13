package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeChunkAdminService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {
    private final KnowledgeChunkAdminService knowledgeChunkAdminService;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public KnowledgeChunkServiceImpl(KnowledgeChunkAdminService knowledgeChunkAdminService,
                                     KnowledgeChunkMapper knowledgeChunkMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.knowledgeChunkAdminService = knowledgeChunkAdminService;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
    }

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        return knowledgeChunkAdminService.page(parseId(docId), requestParam);
    }

    @Override
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam) {
        return knowledgeChunkAdminService.create(parseId(docId), requestParam);
    }

    @Override
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams) {
        knowledgeChunkAdminService.batchCreate(parseId(docId), requestParams, false);
    }

    @Override
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams, boolean writeVector) {
        knowledgeChunkAdminService.batchCreate(parseId(docId), requestParams, writeVector);
    }

    @Override
    public void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam) {
        knowledgeChunkAdminService.update(parseId(docId), parseId(chunkId), requestParam);
    }

    @Override
    public void delete(String docId, String chunkId) {
        knowledgeChunkAdminService.delete(parseId(docId), parseId(chunkId));
    }

    @Override
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        knowledgeChunkAdminService.enableChunk(parseId(docId), parseId(chunkId), enabled);
    }

    @Override
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        knowledgeChunkAdminService.batchToggleEnabled(parseId(docId), requestParam, enabled);
    }

    @Override
    public void updateEnabledByDocId(String docId, String kbId, boolean enabled) {
        Long documentId = parseId(docId);
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
        );
        int enabledValue = enabled ? 1 : 0;
        for (KnowledgeChunkEntity chunk : chunks) {
            chunk.setEnabled(enabledValue);
            knowledgeChunkMapper.updateById(chunk);
        }
    }

    @Override
    public List<KnowledgeChunkVO> listByDocId(String docId) {
        Long documentId = parseId(docId);
        List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, documentId)
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
        );
        return chunks.stream()
                .map(chunk -> {
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
                })
                .toList();
    }

    @Override
    public void deleteByDocId(String docId) {
        Long documentId = parseId(docId);
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, documentId));
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }
}
