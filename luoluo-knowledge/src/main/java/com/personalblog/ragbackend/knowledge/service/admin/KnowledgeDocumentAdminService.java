package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class KnowledgeDocumentAdminService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeAdminSupport support;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;

    public KnowledgeDocumentAdminService(KnowledgeBaseMapper knowledgeBaseMapper,
                                         KnowledgeDocumentMapper knowledgeDocumentMapper,
                                         KnowledgeChunkMapper knowledgeChunkMapper,
                                         KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                         KnowledgeIngestionEngine knowledgeIngestionEngine,
                                         KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                         KnowledgeAdminSupport support,
                                         ObjectProvider<VectorStoreService> vectorStoreServiceProvider) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.support = support;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
    }

    @Transactional
    public KnowledgeDocumentView upload(Long kbId,
                                        KnowledgeDocumentUploadRequest request,
                                        MultipartFile file) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String baseCode = resolveBaseCode(knowledgeBase);
        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(baseCode, file, KnowledgeIngestionMode.INGEST)
        );
        DocumentIngestionSummary summary = result.ingestionSummary();
        if (summary == null) {
            throw new IllegalArgumentException("文档入库失败");
        }
        if (!summary.success()) {
            throw new IllegalArgumentException(summary.errorMessage());
        }
        KnowledgeDocumentEntity entity = requireDocument(summary.documentId());
        applyUploadMetadata(entity, request);
        knowledgeDocumentMapper.updateById(entity);
        return support.toKnowledgeDocumentView(requireDocument(entity.getId()));
    }

    @Transactional
    public void startChunk(Long documentId) {
        requireDocument(documentId);
        throw new IllegalArgumentException("当前版本请重新上传文档以完成重新切块和向量重建");
    }

    @Transactional
    public void delete(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        deleteDocumentArtifacts(document);
        knowledgeDocumentMapper.deleteById(documentId);
    }

    public KnowledgeDocumentView get(Long documentId) {
        return support.toKnowledgeDocumentView(requireDocument(documentId));
    }

    @Transactional
    public void update(Long documentId, KnowledgeDocumentUpdateRequest request) {
        KnowledgeDocumentEntity entity = requireDocument(documentId);
        if (StringUtils.hasText(request.getDocName())) {
            entity.setDocName(request.getDocName().trim());
        }
        if (request.getProcessMode() != null) {
            entity.setProcessMode(blankToNull(request.getProcessMode()));
        }
        if (request.getChunkStrategy() != null) {
            entity.setChunkStrategy(StringUtils.hasText(request.getChunkStrategy())
                    ? support.normalizeChunkStrategy(request.getChunkStrategy())
                    : null);
        }
        if (request.getChunkConfig() != null) {
            entity.setChunkConfig(blankToNull(request.getChunkConfig()));
        }
        if (request.getPipelineId() != null) {
            entity.setPipelineId(request.getPipelineId());
        }
        if (request.getSourceType() != null) {
            entity.setSourceType(blankToNull(request.getSourceType()));
        }
        if (request.getSourceLocation() != null) {
            entity.setSourceLocation(blankToNull(request.getSourceLocation()));
        }
        if (request.getScheduleEnabled() != null) {
            entity.setScheduleEnabled(request.getScheduleEnabled());
        }
        if (request.getScheduleCron() != null) {
            entity.setScheduleCron(blankToNull(request.getScheduleCron()));
        }
        if (request.getStatus() != null) {
            entity.setStatus(blankToNull(request.getStatus()));
        }
        knowledgeDocumentMapper.updateById(entity);
    }

    public IPage<KnowledgeDocumentView> page(Long kbId, KnowledgeDocumentPageRequest request) {
        requireKnowledgeBase(kbId);
        IPage<KnowledgeDocumentEntity> page = knowledgeDocumentMapper.selectPage(
                support.newPage(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKbId, kbId)
                        .eq(request.getEnabled() != null, KnowledgeDocumentEntity::getEnabled, request.getEnabled())
                        .eq(StringUtils.hasText(request.getStatus()), KnowledgeDocumentEntity::getStatus, request.getStatus())
                        .and(StringUtils.hasText(request.getKeyword()), wrapper -> wrapper
                                .like(KnowledgeDocumentEntity::getDocName, request.getKeyword())
                                .or()
                                .like(KnowledgeDocumentEntity::getSourceLocation, request.getKeyword()))
                        .orderByDesc(KnowledgeDocumentEntity::getUpdatedAt)
        );
        return support.mapPage(page, page.getRecords().stream().map(support::toKnowledgeDocumentView).toList());
    }

    public List<KnowledgeDocumentSearchView> search(String keyword, int limit) {
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(KnowledgeDocumentEntity::getDocName, keyword)
                                .or()
                                .like(KnowledgeDocumentEntity::getSourceLocation, keyword))
                        .orderByDesc(KnowledgeDocumentEntity::getUpdatedAt)
                        .last("limit " + Math.max(limit, 1)))
                .stream()
                .map(support::toKnowledgeDocumentSearchView)
                .toList();
    }

    @Transactional
    public void enable(Long documentId, boolean enabled) {
        KnowledgeDocumentEntity entity = requireDocument(documentId);
        entity.setEnabled(enabled ? 1 : 0);
        knowledgeDocumentMapper.updateById(entity);
    }

    private void applyUploadMetadata(KnowledgeDocumentEntity entity, KnowledgeDocumentUploadRequest request) {
        if (request == null) {
            return;
        }
        if (request.getSourceType() != null) {
            entity.setSourceType(blankToNull(request.getSourceType()));
        }
        if (request.getSourceLocation() != null) {
            entity.setSourceLocation(blankToNull(request.getSourceLocation()));
        }
        if (request.getScheduleEnabled() != null) {
            entity.setScheduleEnabled(Boolean.TRUE.equals(request.getScheduleEnabled()) ? 1 : 0);
        }
        if (request.getScheduleCron() != null) {
            entity.setScheduleCron(blankToNull(request.getScheduleCron()));
        }
        if (request.getProcessMode() != null) {
            entity.setProcessMode(blankToNull(request.getProcessMode()));
        }
        if (request.getChunkStrategy() != null) {
            entity.setChunkStrategy(StringUtils.hasText(request.getChunkStrategy())
                    ? support.normalizeChunkStrategy(request.getChunkStrategy())
                    : entity.getChunkStrategy());
        }
        if (request.getChunkConfig() != null) {
            entity.setChunkConfig(blankToNull(request.getChunkConfig()));
        }
        if (request.getPipelineId() != null) {
            entity.setPipelineId(request.getPipelineId());
        }
    }

    private void deleteDocumentArtifacts(KnowledgeDocumentEntity document) {
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        if (vectorStoreService != null) {
            List<String> vectorIds = knowledgeVectorRefMapper.selectList(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                            .eq(KnowledgeVectorRefEntity::getDocId, document.getId()))
                    .stream()
                    .map(KnowledgeVectorRefEntity::getVectorId)
                    .filter(StringUtils::hasText)
                    .toList();
            if (!vectorIds.isEmpty()) {
                vectorStoreService.deleteByIds(
                        vectorSpaceResolver.resolve(String.valueOf(document.getKbId())),
                        vectorIds
                );
            }
        }
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getDocId, document.getId()));
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, document.getId()));
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

    private String resolveBaseCode(KnowledgeBaseEntity knowledgeBase) {
        String normalized = support.normalizeCode(knowledgeBase.getName());
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return String.valueOf(knowledgeBase.getId());
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
