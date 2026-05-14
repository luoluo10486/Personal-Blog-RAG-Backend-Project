package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.IngestionPipelineEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefEntity;
import com.personalblog.ragbackend.knowledge.domain.enums.SourceType;
import com.personalblog.ragbackend.knowledge.domain.enums.ProcessMode;
import com.personalblog.ragbackend.knowledge.config.KnowledgeScheduleProperties;
import com.personalblog.ragbackend.knowledge.handler.RemoteFileFetcher;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentChunkLogMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.IngestionPipelineMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentScheduleService;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionEngine;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionMode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionRequest;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionResult;
import com.personalblog.ragbackend.knowledge.service.ingestion.IngestionPipelineService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.schedule.CronScheduleHelper;
import com.personalblog.ragbackend.rag.dto.StoredFileDTO;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    @Value("knowledge-document-chunk_topic${unique-name:}")
    private String chunkTopic;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final IngestionPipelineMapper ingestionPipelineMapper;
    private final KnowledgeScheduleProperties scheduleProperties;
    private final IngestionPipelineService ingestionPipelineService;
    private final KnowledgeIngestionEngine knowledgeIngestionEngine;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final KnowledgeDocumentScheduleService knowledgeDocumentScheduleService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RemoteFileFetcher remoteFileFetcher;

    public KnowledgeDocumentServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                        KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                        KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                        IngestionPipelineMapper ingestionPipelineMapper,
                                        KnowledgeScheduleProperties scheduleProperties,
                                        IngestionPipelineService ingestionPipelineService,
                                        KnowledgeIngestionEngine knowledgeIngestionEngine,
                                        KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                        KnowledgeFileStorageService knowledgeFileStorageService,
                                        KnowledgeDocumentScheduleService knowledgeDocumentScheduleService,
                                        KnowledgeChunkService knowledgeChunkService,
                                        ObjectProvider<VectorStoreService> vectorStoreServiceProvider,
                                        ObjectProvider<EmbeddingService> embeddingServiceProvider,
                                        ObjectMapper objectMapper,
                                        RocketMQTemplate rocketMQTemplate,
                                        RemoteFileFetcher remoteFileFetcher) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.ingestionPipelineMapper = ingestionPipelineMapper;
        this.scheduleProperties = scheduleProperties;
        this.ingestionPipelineService = ingestionPipelineService;
        this.knowledgeIngestionEngine = knowledgeIngestionEngine;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.knowledgeDocumentScheduleService = knowledgeDocumentScheduleService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.remoteFileFetcher = remoteFileFetcher;
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(parseId(kbId));

        SourceType sourceType = normalizeSourceType(requestParam.getSourceType());
        validateSourceAndSchedule(sourceType, requestParam.getSourceLocation(), requestParam.getScheduleEnabled(), requestParam.getScheduleCron());
        if (sourceType == SourceType.FILE && (file == null || file.isEmpty())) {
            throw new IllegalArgumentException("file is required");
        }
        ProcessMode processMode = normalizeProcessMode(requestParam.getProcessMode());
        String chunkStrategy = null;
        String chunkConfig = null;
        Long pipelineId = null;
        if (ProcessMode.PIPELINE == processMode) {
            if (!StringUtils.hasText(requestParam.getPipelineId())) {
                throw new IllegalArgumentException("pipeline id is required");
            }
            ingestionPipelineService.get(requestParam.getPipelineId());
            pipelineId = parseLong(requestParam.getPipelineId());
        } else {
            chunkStrategy = normalizeChunkStrategy(requestParam.getChunkStrategy());
            chunkConfig = blankToNull(requestParam.getChunkConfig());
        }
        StoredFileDTO storedFile = storeUploadedFile(knowledgeBase.getCollectionName(), requestParam, file);
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKbId(knowledgeBase.getId());
        entity.setDocName(StringUtils.hasText(storedFile.getOriginalFilename()) ? storedFile.getOriginalFilename() : resolveDocName(file));
        entity.setEnabled(1);
        entity.setChunkCount(0);
        entity.setFileUrl(storedFile.getUrl());
        entity.setFileType(storedFile.getDetectedType());
        entity.setFileSize(storedFile.getSize());
        entity.setProcessMode(processMode.getValue());
        entity.setStatus("pending");
        entity.setSourceType(sourceType.getValue());
        entity.setSourceLocation(sourceType == SourceType.URL ? blankToNull(requestParam.getSourceLocation()) : null);
        entity.setScheduleEnabled(sourceType == SourceType.URL && Boolean.TRUE.equals(requestParam.getScheduleEnabled()) ? 1 : 0);
        entity.setScheduleCron(sourceType == SourceType.URL && Boolean.TRUE.equals(requestParam.getScheduleEnabled())
                ? blankToNull(requestParam.getScheduleCron())
                : null);
        entity.setChunkStrategy(chunkStrategy);
        entity.setChunkConfig(chunkConfig);
        entity.setPipelineId(pipelineId);
        entity.setCreatedBy(parseUserId(UserContext.getUserId()));
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeDocumentMapper.insert(entity);
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
        knowledgeDocumentScheduleService.upsertSchedule(document);
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
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(parseId(docId));
        if (document == null) {
            log.warn("document not found, skip chunk task, docId={}", docId);
            return;
        }
        MultipartFile file = knowledgeFileStorageService.restore(document.getFileUrl(), document.getDocName(), document.getFileType());
        if (file == null || file.isEmpty()) {
            return;
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
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        if ("running".equalsIgnoreCase(document.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        deleteDocumentArtifacts(document);
        knowledgeDocumentMapper.deleteById(document.getId());
        knowledgeDocumentChunkLogMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentChunkLogEntity>()
                .eq(KnowledgeDocumentChunkLogEntity::getDocId, document.getId()));
        knowledgeDocumentScheduleService.deleteByDocId(String.valueOf(document.getId()));
        deleteStoredFileQuietly(document.getFileUrl());
    }

    @Override
    public KnowledgeDocumentVO get(String docId) {
        return toView(requireDocument(parseId(docId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String docId, KnowledgeDocumentUpdateRequest requestParam) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        if ("running".equalsIgnoreCase(document.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        if (requestParam == null || !StringUtils.hasText(requestParam.getDocName())) {
            throw new IllegalArgumentException("document name is required");
        }

        SourceType sourceType = normalizeSourceType(document.getSourceType());
        String finalSourceLocation = requestParam.getSourceLocation() != null
                ? blankToNull(requestParam.getSourceLocation())
                : document.getSourceLocation();
        boolean finalScheduleEnabled = requestParam.getScheduleEnabled() != null
                ? requestParam.getScheduleEnabled() == 1
                : document.getScheduleEnabled() != null && document.getScheduleEnabled() == 1;
        String finalScheduleCron = requestParam.getScheduleCron() != null
                ? blankToNull(requestParam.getScheduleCron())
                : document.getScheduleCron();
        validateSourceAndSchedule(sourceType, finalSourceLocation, finalScheduleEnabled, finalScheduleCron);

        if (StringUtils.hasText(requestParam.getDocName())) {
            document.setDocName(requestParam.getDocName().trim());
        }
        boolean scheduleChanged = false;
        if (requestParam.getProcessMode() != null) {
            ProcessMode processMode = normalizeProcessMode(requestParam.getProcessMode());
            document.setProcessMode(processMode.getValue());
            if (ProcessMode.PIPELINE == processMode) {
                if (!StringUtils.hasText(requestParam.getPipelineId())) {
                    throw new IllegalArgumentException("pipeline id is required");
                }
                ingestionPipelineService.get(requestParam.getPipelineId());
                document.setPipelineId(parseLong(requestParam.getPipelineId()));
                document.setChunkStrategy(null);
                document.setChunkConfig(null);
            } else {
                document.setChunkStrategy(requestParam.getChunkStrategy() == null ? document.getChunkStrategy() : normalizeChunkStrategy(requestParam.getChunkStrategy()));
                document.setChunkConfig(requestParam.getChunkConfig() == null ? document.getChunkConfig() : blankToNull(requestParam.getChunkConfig()));
                document.setPipelineId(null);
            }
        } else {
            if (requestParam.getChunkStrategy() != null) {
                document.setChunkStrategy(normalizeChunkStrategy(requestParam.getChunkStrategy()));
            }
            if (requestParam.getChunkConfig() != null) {
                document.setChunkConfig(blankToNull(requestParam.getChunkConfig()));
            }
            if (requestParam.getPipelineId() != null) {
                ingestionPipelineService.get(requestParam.getPipelineId());
                document.setPipelineId(parseLong(requestParam.getPipelineId()));
            }
        }
        if (requestParam.getSourceLocation() != null) {
            document.setSourceLocation(blankToNull(requestParam.getSourceLocation()));
            scheduleChanged = true;
        }
        if (requestParam.getScheduleEnabled() != null) {
            document.setScheduleEnabled(requestParam.getScheduleEnabled());
            scheduleChanged = true;
        }
        if (requestParam.getScheduleCron() != null) {
            document.setScheduleCron(blankToNull(requestParam.getScheduleCron()));
            scheduleChanged = true;
        }
        document.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeDocumentMapper.updateById(document);
        if (scheduleChanged) {
            knowledgeDocumentScheduleService.upsertSchedule(document);
        }
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
    @Transactional(rollbackFor = Exception.class)
    public void enable(String docId, boolean enabled) {
        KnowledgeDocumentEntity document = requireDocument(parseId(docId));
        if ("running".equalsIgnoreCase(document.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKbId());
        int targetEnabled = enabled ? 1 : 0;
        if (document.getEnabled() != null && document.getEnabled() == targetEnabled) {
            return;
        }

        List<KnowledgeChunkVO> chunks = null;
        List<VectorChunk> vectorDocuments = null;
        if (enabled) {
            chunks = knowledgeChunkService.listByDocId(docId);
            if (chunks == null || chunks.isEmpty()) {
                log.warn("enable document skipped because no chunk found, docId={}", docId);
                return;
            }
            EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
            if (embeddingService == null) {
                throw new IllegalStateException("embedding service is unavailable");
            }
            List<String> contents = chunks.stream()
                    .map(KnowledgeChunkVO::getContent)
                    .toList();
            List<List<Float>> embeddings = embeddingService.embedBatch(contents);
            if (embeddings == null || embeddings.size() != chunks.size()) {
                throw new IllegalStateException("embedding result size mismatch");
            }
            vectorDocuments = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                KnowledgeChunkVO chunk = chunks.get(index);
                vectorDocuments.add(VectorChunk.builder()
                        .chunkId(chunk.getId())
                        .content(chunk.getContent())
                        .metadata(buildVectorMetadata(document, chunk))
                        .embedding(toArray(embeddings.get(index)))
                        .index(chunk.getChunkIndex())
                        .build());
            }
        }

        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        if (vectorStoreService == null) {
            throw new IllegalStateException("vector store service is unavailable");
        }
        List<VectorChunk> finalVectorDocuments = vectorDocuments;
        document.setEnabled(enabled ? 1 : 0);
        knowledgeDocumentMapper.updateById(document);
        knowledgeChunkService.updateEnabledByDocId(docId, String.valueOf(knowledgeBase.getId()), enabled);
        if (!enabled) {
            deleteDocumentVectors(document);
        } else if (finalVectorDocuments != null && !finalVectorDocuments.isEmpty()) {
            vectorStoreService.indexDocumentChunks(knowledgeBase.getCollectionName(), String.valueOf(document.getId()), finalVectorDocuments);
            upsertVectorRefs(document, knowledgeBase, chunks);
        }
        knowledgeDocumentScheduleService.syncScheduleIfExists(document);
    }

    @Override
    public List<KnowledgeDocumentSearchVO> search(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        int size = Math.min(Math.max(limit, 1), 20);
        IPage<KnowledgeDocumentEntity> result = knowledgeDocumentMapper.selectPage(
                new Page<>(1, size),
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .like(StringUtils.hasText(keyword), KnowledgeDocumentEntity::getDocName, keyword)
                        .orderByDesc(KnowledgeDocumentEntity::getUpdatedAt)
        );
        List<Long> kbIds = result.getRecords().stream()
                .map(KnowledgeDocumentEntity::getKbId)
                .distinct()
                .toList();
        Map<Long, String> kbNameMap = kbIds.isEmpty()
                ? Map.of()
                : knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .in(KnowledgeBaseEntity::getId, kbIds))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, KnowledgeBaseEntity::getName, (left, right) -> left));
        return result.getRecords().stream().map(entity -> {
            KnowledgeDocumentSearchVO vo = new KnowledgeDocumentSearchVO();
            vo.setId(String.valueOf(entity.getId()));
            vo.setKbId(String.valueOf(entity.getKbId()));
            vo.setDocName(entity.getDocName());
            vo.setKbName(kbNameMap.get(entity.getKbId()));
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
        List<KnowledgeDocumentChunkLogEntity> records = result.getRecords();
        Map<Long, String> pipelineNameMap = new HashMap<>();
        if (!records.isEmpty()) {
            Set<Long> pipelineIds = records.stream()
                    .map(KnowledgeDocumentChunkLogEntity::getPipelineId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            if (!pipelineIds.isEmpty()) {
                List<IngestionPipelineEntity> pipelines = ingestionPipelineMapper.selectByIds(pipelineIds);
                for (IngestionPipelineEntity pipeline : pipelines) {
                    pipelineNameMap.put(pipeline.id, pipeline.name);
                }
            }
        }

        Page<KnowledgeDocumentChunkLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records.stream().map(each -> {
            KnowledgeDocumentChunkLogVO vo = new KnowledgeDocumentChunkLogVO();
            vo.setId(String.valueOf(each.getId()));
            vo.setDocId(String.valueOf(each.getDocId()));
            vo.setStatus(each.getStatus());
            vo.setProcessMode(each.getProcessMode());
            vo.setChunkStrategy(each.getChunkStrategy());
            vo.setPipelineId(each.getPipelineId() == null ? null : String.valueOf(each.getPipelineId()));
            if (each.getPipelineId() != null) {
                vo.setPipelineName(pipelineNameMap.get(each.getPipelineId()));
            }
            vo.setExtractDuration(each.getExtractDuration());
            vo.setChunkDuration(each.getChunkDuration());
            vo.setEmbedDuration(each.getEmbedDuration());
            vo.setPersistDuration(each.getPersistDuration());
            vo.setTotalDuration(each.getTotalDuration());
            if (each.getTotalDuration() != null) {
                long otherDuration = "pipeline".equalsIgnoreCase(each.getProcessMode())
                        ? each.getTotalDuration() - (each.getChunkDuration() == null ? 0 : each.getChunkDuration()) - (each.getPersistDuration() == null ? 0 : each.getPersistDuration())
                        : each.getTotalDuration()
                        - (each.getExtractDuration() == null ? 0 : each.getExtractDuration())
                        - (each.getChunkDuration() == null ? 0 : each.getChunkDuration())
                        - (each.getEmbedDuration() == null ? 0 : each.getEmbedDuration())
                        - (each.getPersistDuration() == null ? 0 : each.getPersistDuration());
                vo.setOtherDuration(Math.max(0, otherDuration));
            }
            vo.setChunkCount(each.getChunkCount());
            vo.setErrorMessage(each.getErrorMessage());
            vo.setStartTime(toDate(each.getStartedAt()));
            vo.setEndTime(toDate(each.getEndedAt()));
            vo.setCreateTime(toDate(each.getCreatedAt()));
            return vo;
        }).toList());
        return voPage;
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
        deleteDocumentVectors(document);
        knowledgeChunkService.deleteByDocId(String.valueOf(document.getId()));
    }

    private void deleteDocumentVectors(KnowledgeDocumentEntity document) {
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        if (vectorStoreService != null) {
            KnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKbId());
            vectorStoreService.deleteDocumentVectors(knowledgeBase.getCollectionName(), String.valueOf(document.getId()));
        }
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefEntity>()
                .eq(KnowledgeVectorRefEntity::getDocId, document.getId()));
    }

    private void upsertVectorRefs(KnowledgeDocumentEntity document,
                                  KnowledgeBaseEntity knowledgeBase,
                                  List<KnowledgeChunkVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int embeddingDim = vectorSpaceResolver.resolve(String.valueOf(knowledgeBase.getId())).dimension();
        for (KnowledgeChunkVO chunk : chunks) {
            KnowledgeVectorRefEntity entity = new KnowledgeVectorRefEntity();
            entity.setKbId(document.getKbId());
            entity.setDocId(document.getId());
            entity.setChunkId(parseLong(chunk.getId()));
            entity.setCollectionName(knowledgeBase.getCollectionName());
            entity.setVectorId(chunk.getId());
            entity.setEmbeddingModel(knowledgeBase.getEmbeddingModel());
            entity.setEmbeddingDim(embeddingDim);
            entity.setMetadata(toJson(buildVectorMetadata(document, chunk)));
            entity.setCreatedBy(parseUserId(UserContext.getUserId()));
            knowledgeVectorRefMapper.insert(entity);
        }
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentEntity document, KnowledgeChunkVO chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId", chunk.getId());
        metadata.put("documentId", String.valueOf(document.getId()));
        metadata.put("knowledgeBaseId", String.valueOf(document.getKbId()));
        metadata.put("baseCode", String.valueOf(document.getKbId()));
        metadata.put("title", document.getDocName());
        metadata.put("sourceUrl", document.getFileUrl());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("sectionTitle", "");
        return metadata;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize metadata", exception);
        }
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

    private StoredFileDTO storeUploadedFile(String collectionName, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (requestParam != null && "url".equalsIgnoreCase(requestParam.getSourceType()) && StringUtils.hasText(requestParam.getSourceLocation())) {
            StoredFileDTO stored = remoteFileFetcher.fetchAndStore(collectionName, requestParam.getSourceLocation());
            if (stored.getOriginalFilename() == null) {
                stored.setOriginalFilename(resolveDocName(file));
            }
            return stored;
        }
        String originalFilename = resolveDocName(file);
        String url = knowledgeFileStorageService.store(file, collectionName, originalFilename);
        return StoredFileDTO.builder()
                .url(url)
                .detectedType(file == null ? null : file.getContentType())
                .size(file == null ? null : file.getSize())
                .originalFilename(originalFilename)
                .build();
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

    private Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private SourceType normalizeSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return SourceType.FILE;
        }
        return SourceType.fromValue(value);
    }

    private ProcessMode normalizeProcessMode(String value) {
        return ProcessMode.fromValue(value);
    }

    private String normalizeChunkStrategy(String value) {
        return ChunkingMode.from(value).getValue();
    }

    private void validateSourceAndSchedule(SourceType sourceType, String sourceLocation, Boolean scheduleEnabled, String scheduleCron) {
        if (sourceType == SourceType.URL && !StringUtils.hasText(sourceLocation)) {
            throw new IllegalArgumentException("source location is required");
        }
        if (sourceType != SourceType.URL || !Boolean.TRUE.equals(scheduleEnabled)) {
            return;
        }
        if (!StringUtils.hasText(scheduleCron)) {
            throw new IllegalArgumentException("schedule cron is required");
        }
        try {
            if (CronScheduleHelper.isIntervalLessThan(scheduleCron, new Date(), scheduleProperties.getMinIntervalSeconds())) {
                throw new IllegalArgumentException("schedule interval is too short");
            }
        } catch (IllegalArgumentException exception) {
            if ("schedule interval is too short".equals(exception.getMessage())) {
                throw exception;
            }
            throw new IllegalArgumentException("schedule cron is invalid", exception);
        }
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

    private float[] toArray(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return new float[0];
        }
        float[] values = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            values[index] = embedding.get(index);
        }
        return values;
    }
}
