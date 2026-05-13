package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.handler.RemoteFileFetcher;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentChunkLogMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentScheduleService;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.rag.dto.StoredFileDTO;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Date;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    @Value("knowledge-document-chunk_topic${unique-name:}")
    private String chunkTopic;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final KnowledgeDocumentScheduleService knowledgeDocumentScheduleService;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final RocketMQTemplate rocketMQTemplate;
    private final RemoteFileFetcher remoteFileFetcher;

    public KnowledgeDocumentServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                        KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        KnowledgeChunkMapper knowledgeChunkMapper,
                                        KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                        KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                        KnowledgeIngestionEngine knowledgeIngestionEngine,
                                        KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                        KnowledgeFileStorageService knowledgeFileStorageService,
                                        KnowledgeDocumentScheduleService knowledgeDocumentScheduleService,
                                        ObjectProvider<VectorStoreService> vectorStoreServiceProvider,
                                        RocketMQTemplate rocketMQTemplate,
                                        RemoteFileFetcher remoteFileFetcher) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.knowledgeDocumentScheduleService = knowledgeDocumentScheduleService;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.rocketMQTemplate = rocketMQTemplate;
        this.remoteFileFetcher = remoteFileFetcher;
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(parseId(kbId));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        String fileUrl = storeUploadedFile(knowledgeBase.getCollectionName(), requestParam, file);
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKbId(knowledgeBase.getId());
        entity.setDocName(resolveDocName(file));
        entity.setEnabled(1);
        entity.setChunkCount(0);
        entity.setFileUrl(fileUrl);
        entity.setFileType(file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setProcessMode(blankToNull(requestParam.getProcessMode()));
        entity.setStatus("running");
        entity.setSourceType(blankToNull(requestParam.getSourceType()));
        entity.setSourceLocation(blankToNull(requestParam.getSourceLocation()));
        entity.setScheduleEnabled(Boolean.TRUE.equals(requestParam.getScheduleEnabled()) ? 1 : 0);
        entity.setScheduleCron(blankToNull(requestParam.getScheduleCron()));
        entity.setChunkStrategy(blankToNull(requestParam.getChunkStrategy()));
        entity.setChunkConfig(blankToNull(requestParam.getChunkConfig()));
        entity.setPipelineId(parseLong(requestParam.getPipelineId()));
        entity.setCreatedBy(null);
        entity.setUpdatedBy(null);
        knowledgeDocumentMapper.insert(entity);
        knowledgeDocumentScheduleService.syncScheduleIfExists(entity);
        return get(String.valueOf(entity.getId()));
    }

    @Override
    @Transactional
    public void startChunk(String docId) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        int updated = knowledgeDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                        .set(KnowledgeDocumentEntity::getStatus, "running")
                        .eq(KnowledgeDocumentEntity::getId, document.getId())
                        .ne(KnowledgeDocumentEntity::getStatus, "running")
        );
        if (updated <= 0) {
            throw new IllegalArgumentException("document is already running");
        }

        String operator = StringUtils.hasText(UserContext.getUsername()) ? UserContext.getUsername() : "system";
        Runnable sendTask = () -> sendChunkMessage(document.getId(), operator);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
            return;
        }
        sendTask.run();
    }

    @Override
    public void executeChunk(String docId) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        MultipartFile file = knowledgeFileStorageService.restore(document.getFileUrl(), document.getDocName(), document.getFileType());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document restore failed");
        }
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKbId());
        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(
                        String.valueOf(knowledgeBase.getId()),
                        file,
                        KnowledgeIngestionMode.INGEST,
                        null,
                        String.valueOf(document.getId()),
                        document.getSourceType(),
                        document.getSourceLocation(),
                        document.getDocName(),
                        document.getFileUrl()
                )
        );
        if (result.ingestionSummary() == null || !result.ingestionSummary().success()) {
            throw new IllegalArgumentException(result.ingestionSummary() == null ? "ingestion summary missing" : result.ingestionSummary().errorMessage());
        }
    }

    @Override
    @Transactional
    public void delete(String docId) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        deleteDocumentArtifacts(document);
        knowledgeDocumentMapper.deleteById(document.getId());
        knowledgeDocumentScheduleService.deleteByDocId(String.valueOf(document.getId()));
        deleteStoredFileQuietly(document.getFileUrl());
    }

    @Override
    public KnowledgeDocumentVO get(String docId) {
        return toView(requireDocument(parseId(docId)));
    }

    @Override
    @Transactional
    public void update(String docId, KnowledgeDocumentUpdateRequest requestParam) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        if (StringUtils.hasText(requestParam.getDocName())) {
            document.setDocName(requestParam.getDocName().trim());
        }
        if (requestParam.getProcessMode() != null) {
            document.setProcessMode(blankToNull(requestParam.getProcessMode()));
        }
        if (requestParam.getChunkStrategy() != null) {
            document.setChunkStrategy(blankToNull(requestParam.getChunkStrategy()));
        }
        if (requestParam.getChunkConfig() != null) {
            document.setChunkConfig(blankToNull(requestParam.getChunkConfig()));
        }
        if (requestParam.getPipelineId() != null) {
            document.setPipelineId(parseLong(requestParam.getPipelineId()));
        }
        if (requestParam.getSourceLocation() != null) {
            document.setSourceLocation(blankToNull(requestParam.getSourceLocation()));
        }
        if (requestParam.getScheduleEnabled() != null) {
            document.setScheduleEnabled(Boolean.TRUE.equals(requestParam.getScheduleEnabled()) ? 1 : 0);
        }
        if (requestParam.getScheduleCron() != null) {
            document.setScheduleCron(blankToNull(requestParam.getScheduleCron()));
        }
        knowledgeDocumentMapper.updateById(document);
        knowledgeDocumentScheduleService.syncScheduleIfExists(document);
    }

    @Override
    public IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam) {
        requireKnowledgeBase(parseId(kbId));
        Page<KnowledgeDocumentEntity> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeDocumentEntity> result = knowledgeDocumentMapper.selectPage(
                page,
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKbId, parseId(kbId))
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .like(StringUtils.hasText(requestParam.getKeyword()), KnowledgeDocumentEntity::getDocName, requestParam.getKeyword())
                        .eq(StringUtils.hasText(requestParam.getStatus()), KnowledgeDocumentEntity::getStatus, requestParam.getStatus())
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
        );
        return result.convert(this::toView);
    }

    @Override
    public void enable(String docId, boolean enabled) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        document.setEnabled(enabled ? 1 : 0);
        knowledgeDocumentMapper.updateById(document);
        knowledgeDocumentScheduleService.syncScheduleIfExists(document);
    }

    @Override
    public List<KnowledgeDocumentSearchVO> search(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        IPage<KnowledgeDocumentEntity> result = knowledgeDocumentMapper.selectPage(
                new Page<>(1, size),
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .like(StringUtils.hasText(keyword), KnowledgeDocumentEntity::getDocName, keyword)
                        .orderByDesc(KnowledgeDocumentEntity::getUpdatedAt)
        );
        return result.getRecords().stream().map(entity -> {
            KnowledgeDocumentSearchVO vo = new KnowledgeDocumentSearchVO();
            vo.setId(String.valueOf(entity.getId()));
            vo.setKbId(String.valueOf(entity.getKbId()));
            vo.setDocName(entity.getDocName());
            KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(entity.getKbId());
            vo.setKbName(kb == null ? null : kb.getName());
            return vo;
        }).toList();
    }

    @Override
    public IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page) {
        Page<KnowledgeDocumentChunkLogEntity> mpPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<KnowledgeDocumentChunkLogEntity> result = knowledgeDocumentChunkLogMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<KnowledgeDocumentChunkLogEntity>()
                        .eq(KnowledgeDocumentChunkLogEntity::getDocId, parseId(docId))
                        .orderByDesc(KnowledgeDocumentChunkLogEntity::getCreatedAt)
        );
        return result.convert(each -> {
            KnowledgeDocumentChunkLogVO vo = new KnowledgeDocumentChunkLogVO();
            vo.setId(String.valueOf(each.getId()));
            vo.setDocId(String.valueOf(each.getDocId()));
            vo.setStatus(each.getStatus());
            vo.setProcessMode(each.getProcessMode());
            vo.setChunkStrategy(each.getChunkStrategy());
            vo.setPipelineId(each.getPipelineId() == null ? null : String.valueOf(each.getPipelineId()));
            vo.setExtractDuration(each.getExtractDuration());
            vo.setChunkDuration(each.getChunkDuration());
            vo.setEmbedDuration(each.getEmbedDuration());
            vo.setPersistDuration(each.getPersistDuration());
            vo.setTotalDuration(each.getTotalDuration());
            vo.setChunkCount(each.getChunkCount());
            vo.setErrorMessage(each.getErrorMessage());
            vo.setStartTime(toDate(each.getStartedAt()));
            vo.setEndTime(toDate(each.getEndedAt()));
            vo.setCreateTime(toDate(each.getCreatedAt()));
            return vo;
        });
    }

    private KnowledgeDocumentVO toView(KnowledgeDocumentEntity entity) {
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
                vectorStoreService.deleteByIds(vectorSpaceResolver.resolve(String.valueOf(document.getKbId())), vectorIds);
            }
        }
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getDocId, document.getId()));
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, document.getId()));
    }

    private void sendChunkMessage(Long documentId, String operator) {
        KnowledgeDocumentChunkEvent event = new KnowledgeDocumentChunkEvent(documentId, operator);
        MessageWrapper<KnowledgeDocumentChunkEvent> wrapper = new MessageWrapper<>();
        wrapper.setKeys(String.valueOf(documentId));
        wrapper.setBody(event);
        rocketMQTemplate.syncSend(
                chunkTopic,
                MessageBuilder.withPayload(wrapper)
                        .setHeader(MessageConst.PROPERTY_KEYS, String.valueOf(documentId))
                        .build()
        );
    }

    private String storeUploadedFile(String collectionName, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (requestParam != null && "url".equalsIgnoreCase(requestParam.getSourceType()) && StringUtils.hasText(requestParam.getSourceLocation())) {
            StoredFileDTO stored = remoteFileFetcher.fetchAndStore(collectionName, requestParam.getSourceLocation());
            return stored.getUrl();
        }
        return knowledgeFileStorageService.store(file, collectionName, resolveDocName(file));
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("knowledge base not found");
        }
        return entity;
    }

    private KnowledgeDocumentEntity requireDocument(Long docId) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(docId);
        if (entity == null) {
            throw new IllegalArgumentException("document not found");
        }
        return entity;
    }

    private Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveDocName(MultipartFile file) {
        return file == null || !StringUtils.hasText(file.getOriginalFilename()) ? "uploaded-document" : file.getOriginalFilename().trim();
    }

    private void deleteStoredFileQuietly(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            knowledgeFileStorageService.deleteByUrl(fileUrl);
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }

    private Date toDate(java.time.LocalDateTime value) {
        return value == null ? null : Date.from(value.atZone(java.time.ZoneId.systemDefault()).toInstant());
    }
}
