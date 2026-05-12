package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentChunkLogView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentChunkLogMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class KnowledgeDocumentAdminService {
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
    private final KnowledgeAdminSupport support;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final RocketMQTemplate rocketMQTemplate;

    public KnowledgeDocumentAdminService(KnowledgeBaseMapper knowledgeBaseMapper,
                                         KnowledgeDocumentMapper knowledgeDocumentMapper,
                                         KnowledgeChunkMapper knowledgeChunkMapper,
                                         KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                         KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                         KnowledgeIngestionEngine knowledgeIngestionEngine,
                                         KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                         KnowledgeFileStorageService knowledgeFileStorageService,
                                         KnowledgeAdminSupport support,
                                         ObjectProvider<VectorStoreService> vectorStoreServiceProvider,
                                         RocketMQTemplate rocketMQTemplate) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.support = support;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Transactional
    public KnowledgeDocumentView upload(Long kbId, KnowledgeDocumentUploadRequest request, MultipartFile file) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(kbId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("涓婁紶鏂囦欢涓嶈兘涓虹┖");
        }
        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(resolveBaseCode(knowledgeBase), file, KnowledgeIngestionMode.INGEST)
        );
        DocumentIngestionSummary summary = result.ingestionSummary();
        if (summary == null || !summary.success()) {
            throw new IllegalArgumentException(summary == null ? "鏂囨。鍏ュ簱澶辫触" : summary.errorMessage());
        }
        KnowledgeDocumentEntity entity = requireDocument(summary.documentId());
        applyUploadMetadata(entity, request);
        knowledgeDocumentMapper.updateById(entity);
        return support.toKnowledgeDocumentView(requireDocument(entity.getId()));
    }

    @Transactional
    public void startChunk(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        if (!StringUtils.hasText(document.getFileUrl())) {
            throw new IllegalArgumentException("鏂囨。娌℃湁鍙敤鐨勬簮鏂囦欢璺緞锛岃閲嶆柊涓婁紶");
        }

        int updated = knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                .set(KnowledgeDocumentEntity::getStatus, "running")
                .eq(KnowledgeDocumentEntity::getId, documentId)
                .ne(KnowledgeDocumentEntity::getStatus, "running"));
        if (updated <= 0) {
            throw new IllegalArgumentException("鏂囨。姝ｅ湪鍒嗗潡涓紝璇风◢鍚庡啀璇�");
        }

        String operator = StringUtils.hasText(UserContext.getUsername()) ? UserContext.getUsername() : "system";
        Runnable sendTask = () -> sendChunkMessage(documentId, operator);
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

    public void executeChunk(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        MultipartFile file = knowledgeFileStorageService.restore(
                document.getFileUrl(),
                document.getDocName(),
                document.getFileType()
        );
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("鏂囨。婧愭枃浠朵笉瀛樺湪");
        }
        KnowledgeIngestionResult result = knowledgeIngestionEngine.execute(
                new KnowledgeIngestionRequest(
                        resolveBaseCode(requireKnowledgeBase(document.getKbId())),
                        file,
                        KnowledgeIngestionMode.INGEST,
                        null,
                        String.valueOf(documentId),
                        document.getSourceType(),
                        document.getSourceLocation(),
                        document.getDocName(),
                        document.getFileUrl()
                )
        );
        DocumentIngestionSummary summary = result.ingestionSummary();
        if (summary == null || !summary.success()) {
            throw new IllegalArgumentException(summary == null ? "閲嶆柊鍒嗗潡澶辫触" : summary.errorMessage());
        }
    }

    @Transactional
    public void delete(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        String fileUrl = document.getFileUrl();
        deleteDocumentArtifacts(document);
        knowledgeDocumentMapper.deleteById(documentId);
        deleteStoredFileQuietly(fileUrl);
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

    public IPage<KnowledgeDocumentChunkLogView> pageChunkLogs(Long documentId, long current, long size) {
        requireDocument(documentId);
        IPage<KnowledgeDocumentChunkLogEntity> page = knowledgeDocumentChunkLogMapper.selectPage(
                support.newPage(current, size),
                new LambdaQueryWrapper<KnowledgeDocumentChunkLogEntity>()
                        .eq(KnowledgeDocumentChunkLogEntity::getDocId, documentId)
                        .orderByDesc(KnowledgeDocumentChunkLogEntity::getStartedAt)
                        .orderByDesc(KnowledgeDocumentChunkLogEntity::getId)
        );
        return support.mapPage(page, page.getRecords().stream().map(support::toKnowledgeDocumentChunkLogView).toList());
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
                vectorStoreService.deleteByIds(vectorSpaceResolver.resolve(String.valueOf(document.getKbId())), vectorIds);
            }
        }
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getDocId, document.getId()));
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocId, document.getId()));
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("鐭ヨ瘑搴揑D 涓嶈兘涓虹┖");
        }
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("鐭ヨ瘑搴撲笉瀛樺湪");
        }
        return entity;
    }

    private KnowledgeDocumentEntity requireDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("鏂囨。 ID 涓嶈兘涓虹┖");
        }
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(documentId);
        if (entity == null) {
            throw new IllegalArgumentException("鏂囨。涓嶅瓨鍦�");
        }
        return entity;
    }

    private String resolveBaseCode(KnowledgeBaseEntity knowledgeBase) {
        String normalized = support.normalizeCode(knowledgeBase.getName());
        return StringUtils.hasText(normalized) ? normalized : String.valueOf(knowledgeBase.getId());
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private void deleteStoredFileQuietly(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            knowledgeFileStorageService.deleteByUrl(fileUrl);
        } catch (Exception ignored) {
            // ignore
        }
    }
}
