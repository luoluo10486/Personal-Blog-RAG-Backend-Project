package com.personalblog.ragbackend.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import com.personalblog.ragbackend.infra.token.TokenCounterService;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkDO;
import com.personalblog.ragbackend.ingestion.dao.entity.IngestionPipelineDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefDO;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.domain.enums.SourceType;
import com.personalblog.ragbackend.knowledge.domain.enums.ProcessMode;
import com.personalblog.ragbackend.knowledge.domain.enums.DocumentStatus;
import com.personalblog.ragbackend.knowledge.config.KnowledgeScheduleProperties;
import com.personalblog.ragbackend.core.parser.DocumentParser;
import com.personalblog.ragbackend.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.handler.RemoteFileFetcher;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentChunkLogMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.ingestion.dao.mapper.IngestionPipelineMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentScheduleService;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeFileStorageService;
import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.context.NodeLog;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionStatus;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;
import com.personalblog.ragbackend.ingestion.engine.IngestionEngine;
import com.personalblog.ragbackend.ingestion.service.IngestionPipelineService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.schedule.CronScheduleHelper;
import com.personalblog.ragbackend.rag.dto.StoredFileDTO;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final IngestionPipelineMapper ingestionPipelineMapper;
    private final KnowledgeScheduleProperties scheduleProperties;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionEngine ingestionEngine;
    private final DocumentParserSelector documentParserSelector;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final KnowledgeDocumentScheduleService knowledgeDocumentScheduleService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final TokenCounterService tokenCounterService;
    private final TransactionOperations transactionOperations;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RemoteFileFetcher remoteFileFetcher;

    public KnowledgeDocumentServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                        KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        KnowledgeChunkMapper knowledgeChunkMapper,
                                        KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                        KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                        IngestionPipelineMapper ingestionPipelineMapper,
                                        KnowledgeScheduleProperties scheduleProperties,
                                        IngestionPipelineService ingestionPipelineService,
                                        IngestionEngine ingestionEngine,
                                        DocumentParserSelector documentParserSelector,
                                        KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                        KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                        KnowledgeFileStorageService knowledgeFileStorageService,
                                        KnowledgeDocumentScheduleService knowledgeDocumentScheduleService,
                                        KnowledgeChunkService knowledgeChunkService,
                                        VectorStoreService vectorStoreService,
                                        EmbeddingService embeddingService,
                                        TokenCounterService tokenCounterService,
                                        TransactionOperations transactionOperations,
                                        ObjectMapper objectMapper,
                                        RocketMQTemplate rocketMQTemplate,
                                        RemoteFileFetcher remoteFileFetcher) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.ingestionPipelineMapper = ingestionPipelineMapper;
        this.scheduleProperties = scheduleProperties;
        this.ingestionPipelineService = ingestionPipelineService;
        this.ingestionEngine = ingestionEngine;
        this.documentParserSelector = documentParserSelector;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.knowledgeDocumentScheduleService = knowledgeDocumentScheduleService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.tokenCounterService = tokenCounterService;
        this.transactionOperations = transactionOperations;
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.remoteFileFetcher = remoteFileFetcher;
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(parseId(kbId));

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
        KnowledgeDocumentDO entity = new KnowledgeDocumentDO();
        entity.setKbId(knowledgeBase.getId());
        entity.setDocName(StringUtils.hasText(storedFile.getOriginalFilename()) ? storedFile.getOriginalFilename() : resolveDocName(file));
        entity.setEnabled(1);
        entity.setChunkCount(0);
        entity.setFileUrl(storedFile.getUrl());
        entity.setFileType(storedFile.getDetectedType());
        entity.setFileSize(storedFile.getSize());
        entity.setProcessMode(processMode.getValue());
        entity.setStatus(DocumentStatus.PENDING.getCode());
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
        KnowledgeDocumentDO document = requireDocument(parseId(docId));
        int updated = knowledgeDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocumentDO>()
                        .set(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
                        .eq(KnowledgeDocumentDO::getId, document.getId())
                        .ne(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
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
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(parseId(docId));
        if (document == null) {
            log.warn("document not found, skip chunk task, docId={}", docId);
            return;
        }
        runChunkTask(document);
    }

    private record ChunkProcessResult(List<VectorChunk> chunks, long extractDuration, long chunkDuration,
                                      long embedDuration) {
    }

    private void runChunkTask(KnowledgeDocumentDO document) {
        String docId = String.valueOf(document.getId());
        ProcessMode processMode = normalizeProcessMode(document.getProcessMode());
        KnowledgeDocumentChunkLogDO chunkLog = insertChunkLog(document);

        long totalStartTime = System.currentTimeMillis();
        long extractDuration = 0L;
        long chunkDuration = 0L;
        long embedDuration = 0L;
        long persistDuration = 0L;

        try {
            List<VectorChunk> chunkResults;
            if (ProcessMode.PIPELINE == processMode) {
                ChunkProcessResult result = runPipelineProcess(document);
                chunkResults = result.chunks();
                extractDuration = result.extractDuration();
                chunkDuration = result.chunkDuration();
                embedDuration = result.embedDuration();
            } else {
                ChunkProcessResult result = runChunkProcess(document);
                chunkResults = result.chunks();
                extractDuration = result.extractDuration();
                chunkDuration = result.chunkDuration();
                embedDuration = result.embedDuration();
            }

            long persistStart = System.currentTimeMillis();
            int savedCount = persistChunksAndVectorsAtomically(document, chunkResults);
            persistDuration = System.currentTimeMillis() - persistStart;

            long totalDuration = System.currentTimeMillis() - totalStartTime;
            updateChunkLog(chunkLog.getId(), DocumentStatus.SUCCESS.getCode(), savedCount, extractDuration, chunkDuration, embedDuration, persistDuration, totalDuration, null);
        } catch (Exception exception) {
            log.error("document chunk task failed, docId={}", docId, exception);
            markChunkFailed(document.getId());
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            updateChunkLog(chunkLog.getId(), DocumentStatus.FAILED.getCode(), 0, extractDuration, chunkDuration, embedDuration, persistDuration, totalDuration, exception.getMessage());
        }
    }

    private ChunkProcessResult runChunkProcess(KnowledgeDocumentDO document) {
        MultipartFile file = knowledgeFileStorageService.restore(document.getFileUrl(), document.getDocName(), document.getFileType());
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("document file is unavailable");
        }

        long extractStart = System.currentTimeMillis();
        DocumentParser parser = documentParserSelector.select(file.getContentType(), document.getDocName());
        ParseResult parseResult;
        try (InputStream inputStream = file.getInputStream()) {
            parseResult = parser.parse(inputStream, document.getDocName(), file.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read document file", exception);
        }
        long extractDuration = System.currentTimeMillis() - extractStart;

        if (parseResult == null || !parseResult.success()) {
            throw new IllegalStateException(parseResult == null ? "document parsing failed" : parseResult.errorMessage());
        }

        TextChunkingOptions options = buildChunkingOptions(document);
        long chunkStart = System.currentTimeMillis();
        DocumentChunkResponse chunkResponse = knowledgeDocumentChunkService.chunkContent(
                parseResult.content(),
                parseResult.mimeType(),
                parseResult.metadata(),
                parseResult.contentLength(),
                options
        );
        long chunkDuration = System.currentTimeMillis() - chunkStart;

        if (chunkResponse == null || !chunkResponse.success()) {
            throw new IllegalStateException(chunkResponse == null ? "document chunking failed" : chunkResponse.errorMessage());
        }

        long embedStart = System.currentTimeMillis();
        List<List<Float>> embeddings = embeddingService.embedBatch(
                chunkResponse.chunks().stream().map(DocumentChunk::content).toList()
        );
        long embedDuration = System.currentTimeMillis() - embedStart;

        List<VectorChunk> vectorChunks = buildVectorChunks(document, chunkResponse.chunks(), embeddings);
        return new ChunkProcessResult(vectorChunks, extractDuration, chunkDuration, embedDuration);
    }

    private ChunkProcessResult runPipelineProcess(KnowledgeDocumentDO document) {
        String docId = String.valueOf(document.getId());
        if (document.getPipelineId() == null) {
            throw new IllegalStateException("Pipeline mode requires pipeline id: docId=" + docId);
        }

        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(document.getKbId());
        MultipartFile file = knowledgeFileStorageService.restore(document.getFileUrl(), document.getDocName(), document.getFileType());
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("document file is unavailable");
        }

        PipelineDefinition pipeline = ingestionPipelineService.getDefinition(String.valueOf(document.getPipelineId()));
        IngestionContext previewContext = IngestionContext.builder()
                .taskId(docId)
                .pipelineId(String.valueOf(document.getPipelineId()))
                .source(DocumentSource.builder()
                        .type(com.personalblog.ragbackend.ingestion.domain.enums.SourceType.FILE)
                        .location(document.getSourceLocation())
                        .fileName(document.getDocName())
                        .build())
                .rawBytes(getBytes(file))
                .mimeType(file.getContentType())
                .vectorSpaceId(com.personalblog.ragbackend.rag.core.vector.VectorSpaceId.builder()
                        .logicalName(knowledgeBase.getCollectionName())
                        .build())
                .status(IngestionStatus.RUNNING)
                .logs(new ArrayList<>())
                .metadata(new HashMap<>())
                .skipIndexerWrite(true)
                .build();

        IngestionContext result = ingestionEngine.execute(pipeline, previewContext);
        if (result.getStatus() == IngestionStatus.FAILED || result.getError() != null) {
            String message = result.getError() == null ? "pipeline chunking failed" : result.getError().getMessage();
            throw new IllegalStateException(message);
        }

        List<VectorChunk> vectorChunks = result.getChunks() == null ? List.of() : result.getChunks();
        if (vectorChunks.isEmpty()) {
            throw new IllegalStateException("pipeline chunking failed");
        }
        List<List<Float>> embeddings = vectorChunks.stream()
                .map(chunk -> toEmbeddingList(chunk.getEmbedding()))
                .toList();
        DocumentChunkResponse chunkResponse = DocumentChunkResponse.success(
                result.getMimeType(),
                result.getMetadata() == null ? Map.<String, String>of() : result.getMetadata().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue()))),
                result.getRawText() == null ? 0 : result.getRawText().length(),
                512,
                640,
                128,
                vectorChunks.stream()
                        .map(chunk -> new DocumentChunk(
                                chunk.getIndex() == null ? 0 : chunk.getIndex(),
                                chunk.getMetadata() != null && chunk.getMetadata().get("sectionTitle") != null
                                        ? String.valueOf(chunk.getMetadata().get("sectionTitle"))
                                        : null,
                                chunk.getContent(),
                                chunk.getContent() == null ? 0 : chunk.getContent().length(),
                                Boolean.TRUE.equals(chunk.getMetadata() == null ? null : chunk.getMetadata().get("overlapFromPrevious"))
                        ))
                        .toList()
        );
        long extractDuration = resolveNodeDuration(result.getLogs(), "parser");
        long chunkDuration = resolveMetadataDuration(result.getMetadata(), "chunkDurationMs", resolveNodeDuration(result.getLogs(), "chunker"));
        long embedDuration = resolveMetadataDuration(result.getMetadata(), "embedDurationMs", chunkDuration);
        List<VectorChunk> vectorChunksWithEmbeddings = buildVectorChunks(document, chunkResponse.chunks(), embeddings);
        return new ChunkProcessResult(vectorChunksWithEmbeddings, extractDuration, chunkDuration, embedDuration);
    }

    private KnowledgeDocumentChunkLogDO insertChunkLog(KnowledgeDocumentDO document) {
        KnowledgeDocumentChunkLogDO entity = new KnowledgeDocumentChunkLogDO();
        entity.setDocId(document.getId());
        entity.setStatus(DocumentStatus.RUNNING.getCode());
        entity.setProcessMode(document.getProcessMode());
        entity.setChunkStrategy(document.getChunkStrategy());
        entity.setPipelineId(document.getPipelineId());
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentChunkLogMapper.insert(entity);
        return entity;
    }

    private void updateChunkLog(Long logId,
                                String status,
                                int chunkCount,
                                long extractDuration,
                                long chunkDuration,
                                long embedDuration,
                                long persistDuration,
                                long totalDuration,
                                String errorMessage) {
        KnowledgeDocumentChunkLogDO entity = new KnowledgeDocumentChunkLogDO();
        entity.setId(logId);
        entity.setStatus(status);
        entity.setChunkCount(chunkCount);
        entity.setExtractDuration(extractDuration);
        entity.setChunkDuration(chunkDuration);
        entity.setEmbedDuration(embedDuration);
        entity.setPersistDuration(persistDuration);
        entity.setTotalDuration(totalDuration);
        entity.setErrorMessage(errorMessage);
        entity.setEndedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentChunkLogMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId) {
        KnowledgeDocumentDO document = requireDocument(parseId(docId));
        if (DocumentStatus.RUNNING.getCode().equalsIgnoreCase(document.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        deleteDocumentArtifacts(document);
        knowledgeDocumentMapper.deleteById(document.getId());
        knowledgeDocumentChunkLogMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentChunkLogDO>()
                .eq(KnowledgeDocumentChunkLogDO::getDocId, document.getId()));
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
        KnowledgeDocumentDO document = requireDocument(parseId(docId));
        if (DocumentStatus.RUNNING.getCode().equalsIgnoreCase(document.getStatus())) {
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
                ? requestParam.getScheduleEnabled()
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
            document.setScheduleEnabled(Boolean.TRUE.equals(requestParam.getScheduleEnabled()) ? 1 : 0);
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
        Page<KnowledgeDocumentDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeDocumentDO> result = knowledgeDocumentMapper.selectPage(
                page,
                new LambdaQueryWrapper<KnowledgeDocumentDO>()
                        .eq(KnowledgeDocumentDO::getKbId, parseId(kbId))
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .like(StringUtils.hasText(requestParam.getKeyword()), KnowledgeDocumentDO::getDocName, requestParam.getKeyword())
                        .eq(StringUtils.hasText(requestParam.getStatus()), KnowledgeDocumentDO::getStatus, requestParam.getStatus())
                        .orderByDesc(KnowledgeDocumentDO::getCreatedAt)
        );
        return result.convert(this::toView);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String docId, boolean enabled) {
        KnowledgeDocumentDO document = requireDocument(parseId(docId));
        if (DocumentStatus.RUNNING.getCode().equalsIgnoreCase(document.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(document.getKbId());
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
        IPage<KnowledgeDocumentDO> result = knowledgeDocumentMapper.selectPage(
                new Page<>(1, size),
                new LambdaQueryWrapper<KnowledgeDocumentDO>()
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .like(StringUtils.hasText(keyword), KnowledgeDocumentDO::getDocName, keyword)
                        .orderByDesc(KnowledgeDocumentDO::getUpdatedAt)
        );
        List<Long> kbIds = result.getRecords().stream()
                .map(KnowledgeDocumentDO::getKbId)
                .distinct()
                .toList();
        Map<Long, String> kbNameMap = kbIds.isEmpty()
                ? Map.of()
                : knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .in(KnowledgeBaseDO::getId, kbIds))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseDO::getId, KnowledgeBaseDO::getName, (left, right) -> left));
        return result.getRecords().stream().map(entity -> {
            KnowledgeDocumentSearchVO vo = BeanUtil.toBean(entity, KnowledgeDocumentSearchVO.class);
            vo.setId(String.valueOf(entity.getId()));
            vo.setKbId(String.valueOf(entity.getKbId()));
            vo.setKbName(kbNameMap.get(entity.getKbId()));
            return vo;
        }).toList();
    }

    @Override
    public IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page) {
        Page<KnowledgeDocumentChunkLogDO> mpPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<KnowledgeDocumentChunkLogDO> result = knowledgeDocumentChunkLogMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<KnowledgeDocumentChunkLogDO>()
                        .eq(KnowledgeDocumentChunkLogDO::getDocId, parseId(docId))
                        .orderByDesc(KnowledgeDocumentChunkLogDO::getCreatedAt)
        );
        List<KnowledgeDocumentChunkLogDO> records = result.getRecords();
        Map<Long, String> pipelineNameMap = new HashMap<>();
        if (!records.isEmpty()) {
            Set<Long> pipelineIds = records.stream()
                    .map(KnowledgeDocumentChunkLogDO::getPipelineId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            if (!pipelineIds.isEmpty()) {
                List<IngestionPipelineDO> pipelines = ingestionPipelineMapper.selectByIds(pipelineIds);
                for (IngestionPipelineDO pipeline : pipelines) {
                    pipelineNameMap.put(pipeline.id, pipeline.name);
                }
            }
        }

        Page<KnowledgeDocumentChunkLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records.stream().map(each -> {
            KnowledgeDocumentChunkLogVO vo = BeanUtil.toBean(each, KnowledgeDocumentChunkLogVO.class);
            vo.setId(String.valueOf(each.getId()));
            vo.setDocId(String.valueOf(each.getDocId()));
            vo.setPipelineId(each.getPipelineId() == null ? null : String.valueOf(each.getPipelineId()));
            vo.setDurationMs(each.getTotalDuration());
            vo.setMessage(each.getMessage());
            vo.setRemark(StringUtils.hasText(each.getMessage()) ? each.getMessage() : each.getErrorMessage());
            if (each.getPipelineId() != null) {
                vo.setPipelineName(pipelineNameMap.get(each.getPipelineId()));
            }
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
            return vo;
        }).toList());
        return voPage;
    }

    private KnowledgeDocumentVO toView(KnowledgeDocumentDO entity) {
        KnowledgeDocumentVO vo = BeanUtil.toBean(entity, KnowledgeDocumentVO.class);
        vo.setId(String.valueOf(entity.getId()));
        vo.setKbId(String.valueOf(entity.getKbId()));
        vo.setPipelineId(entity.getPipelineId() == null ? null : String.valueOf(entity.getPipelineId()));
        vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        vo.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        vo.setCreateTime(entity.getCreatedAt());
        vo.setUpdateTime(entity.getUpdatedAt());
        return vo;
    }

    private void deleteDocumentArtifacts(KnowledgeDocumentDO document) {
        deleteDocumentVectors(document);
        knowledgeChunkService.deleteByDocId(String.valueOf(document.getId()));
    }

    private void deleteDocumentVectors(KnowledgeDocumentDO document) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(document.getKbId());
        vectorStoreService.deleteDocumentVectors(knowledgeBase.getCollectionName(), String.valueOf(document.getId()));
        knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefDO>()
                .eq(KnowledgeVectorRefDO::getDocId, document.getId()));
    }

    private List<VectorChunk> buildVectorChunks(KnowledgeDocumentDO document,
                                                List<DocumentChunk> chunks,
                                                List<List<Float>> embeddings) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("embedding result size mismatch");
        }
        List<VectorChunk> vectorChunks = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = chunks.get(index);
            vectorChunks.add(VectorChunk.builder()
                    .content(chunk.content())
                    .index(chunk.chunkIndex())
                    .metadata(buildVectorMetadata(document, chunk))
                    .embedding(toArray(embeddings.get(index)))
                    .build());
        }
        return vectorChunks;
    }

    private TextChunkingOptions buildChunkingOptions(KnowledgeDocumentDO document) {
        ChunkingMode chunkingMode = ChunkingMode.from(document.getChunkStrategy());
        Map<String, Object> chunkConfig = readChunkConfig(document.getChunkConfig());
        if (ChunkingMode.FIXED_SIZE == chunkingMode) {
            int chunkSize = intValue(chunkConfig, "chunkSize", 512);
            int overlapSize = intValue(chunkConfig, "overlapSize", 128);
            int maxChunkSize = Math.max(chunkSize, chunkSize + Math.max(0, overlapSize));
            return new TextChunkingOptions(chunkSize, maxChunkSize, overlapSize, 1000);
        }
        int targetChars = intValue(chunkConfig, "targetChars", 1400);
        int overlapChars = intValue(chunkConfig, "overlapChars", 0);
        int maxChars = intValue(chunkConfig, "maxChars", 1800);
        return new TextChunkingOptions(targetChars, Math.max(targetChars, maxChars), overlapChars, 1000);
    }

    private void markChunkFailed(Long docId) {
        transactionOperations.executeWithoutResult(status -> {
            KnowledgeDocumentDO update = new KnowledgeDocumentDO();
            update.setId(docId);
            update.setStatus(DocumentStatus.FAILED.getCode());
            update.setUpdatedBy(parseUserId(UserContext.getUserId()));
            knowledgeDocumentMapper.updateById(update);
        });
    }

    private long resolveNodeDuration(List<NodeLog> nodeLogs, String nodeType) {
        if (nodeLogs == null || nodeLogs.isEmpty() || !StringUtils.hasText(nodeType)) {
            return 0L;
        }
        return nodeLogs.stream()
                .filter(each -> nodeType.equalsIgnoreCase(each.getNodeType()) || nodeType.equalsIgnoreCase(each.getNodeId()))
                .mapToLong(NodeLog::getDurationMs)
                .sum();
    }

    private long resolveMetadataDuration(Map<String, Object> metadata, String key, long fallback) {
        if (metadata == null || !metadata.containsKey(key)) {
            return fallback;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<Float> toEmbeddingList(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        List<Float> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add(value);
        }
        return values;
    }

    private byte[] getBytes(MultipartFile file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("read file bytes failed", exception);
        }
    }

    private Map<String, Object> readChunkConfig(String chunkConfig) {
        if (!StringUtils.hasText(chunkConfig)) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(chunkConfig, Map.class);
            return result == null ? Map.of() : result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("chunk config is invalid", exception);
        }
    }

    private int intValue(Map<String, Object> config, String key, int defaultValue) {
        if (config == null || !config.containsKey(key) || config.get(key) == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int persistChunksAndVectorsAtomically(KnowledgeDocumentDO document,
                                                  List<VectorChunk> chunkResults) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(document.getKbId());
        List<VectorChunk> safeChunks = chunkResults == null ? List.of() : chunkResults;
        transactionOperations.executeWithoutResult(status -> {
            knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>()
                    .eq(KnowledgeChunkDO::getDocId, document.getId()));
            knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefDO>()
                    .eq(KnowledgeVectorRefDO::getDocId, document.getId()));

            vectorStoreService.deleteDocumentVectors(knowledgeBase.getCollectionName(), String.valueOf(document.getId()));

            List<KnowledgeChunkDO> persistedChunks = new ArrayList<>(safeChunks.size());
            for (VectorChunk chunk : safeChunks) {
                KnowledgeChunkDO entity = new KnowledgeChunkDO();
                entity.setKbId(document.getKbId());
                entity.setDocId(document.getId());
                entity.setChunkIndex(chunk.getIndex());
                entity.setContent(chunk.getContent());
                entity.setContentHash(sha256Hex(chunk.getContent()));
                entity.setCharCount(chunk.getContent() == null ? 0 : chunk.getContent().length());
                entity.setTokenCount(resolveTokenCount(chunk.getContent()));
                entity.setEnabled(1);
                entity.setMetadata(toJson(chunk.getMetadata()));
                entity.setCreatedBy(parseUserId(UserContext.getUserId()));
                entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
                knowledgeChunkMapper.insert(entity);
                chunk.setChunkId(String.valueOf(entity.getId()));
                persistedChunks.add(entity);
            }

            if (!safeChunks.isEmpty()) {
                vectorStoreService.indexDocumentChunks(knowledgeBase.getCollectionName(), String.valueOf(document.getId()), safeChunks);
                insertVectorRefs(document, knowledgeBase, persistedChunks, safeChunks);
            }

            document.setChunkCount(safeChunks.size());
            document.setStatus(DocumentStatus.SUCCESS.getCode());
            document.setUpdatedBy(parseUserId(UserContext.getUserId()));
            knowledgeDocumentMapper.updateById(document);
        });
        return safeChunks.size();
    }

    private void insertVectorRefs(KnowledgeDocumentDO document,
                                  KnowledgeBaseDO knowledgeBase,
                                  List<KnowledgeChunkDO> persistedChunks,
                                  List<VectorChunk> vectorChunks) {
        if (persistedChunks == null || persistedChunks.isEmpty()) {
            return;
        }
        int embeddingDim = vectorSpaceResolver.resolve(String.valueOf(knowledgeBase.getId())).dimension();
        for (int index = 0; index < persistedChunks.size(); index++) {
            KnowledgeChunkDO chunkEntity = persistedChunks.get(index);
            VectorChunk vectorChunk = vectorChunks.get(index);
            KnowledgeVectorRefDO entity = new KnowledgeVectorRefDO();
            entity.setKbId(document.getKbId());
            entity.setDocId(document.getId());
            entity.setChunkId(chunkEntity.getId());
            entity.setCollectionName(knowledgeBase.getCollectionName());
            entity.setVectorId(String.valueOf(chunkEntity.getId()));
            entity.setEmbeddingModel(knowledgeBase.getEmbeddingModel());
            entity.setEmbeddingDim(embeddingDim);
            entity.setMetadata(toJson(vectorChunk.getMetadata()));
            entity.setCreatedBy(parseUserId(UserContext.getUserId()));
            knowledgeVectorRefMapper.insert(entity);
        }
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentDO document, DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        String documentId = String.valueOf(document.getId());
        String knowledgeBaseId = String.valueOf(document.getKbId());
        metadata.put("documentId", documentId);
        metadata.put("docId", documentId);
        metadata.put("doc_id", documentId);
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("kbId", knowledgeBaseId);
        metadata.put("kb_id", knowledgeBaseId);
        metadata.put("baseCode", String.valueOf(document.getKbId()));
        metadata.put("title", document.getDocName());
        metadata.put("sourceUrl", document.getFileUrl());
        metadata.put("chunkIndex", chunk.chunkIndex());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.put("sectionTitle", chunk.sectionTitle());
        metadata.put("contentLength", chunk.contentLength());
        metadata.put("overlapFromPrevious", chunk.overlapFromPrevious());
        return metadata;
    }

    private void upsertVectorRefs(KnowledgeDocumentDO document,
                                  KnowledgeBaseDO knowledgeBase,
                                  List<KnowledgeChunkVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int embeddingDim = vectorSpaceResolver.resolve(String.valueOf(knowledgeBase.getId())).dimension();
        for (KnowledgeChunkVO chunk : chunks) {
            KnowledgeVectorRefDO entity = new KnowledgeVectorRefDO();
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

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentDO document, KnowledgeChunkVO chunk) {
        Map<String, Object> metadata = new HashMap<>();
        String documentId = String.valueOf(document.getId());
        String knowledgeBaseId = String.valueOf(document.getKbId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunk_id", chunk.getId());
        metadata.put("documentId", documentId);
        metadata.put("docId", documentId);
        metadata.put("doc_id", documentId);
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("kbId", knowledgeBaseId);
        metadata.put("kb_id", knowledgeBaseId);
        metadata.put("baseCode", String.valueOf(document.getKbId()));
        metadata.put("title", document.getDocName());
        metadata.put("sourceUrl", document.getFileUrl());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("chunk_index", chunk.getChunkIndex());
        metadata.put("sectionTitle", "");
        return metadata;
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                String hex = Integer.toHexString(Byte.toUnsignedInt(value));
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("failed to hash chunk content", exception);
        }
    }

    private Integer resolveTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        Integer tokenCount = tokenCounterService.countTokens(content);
        return tokenCount == null ? 0 : tokenCount;
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

    private KnowledgeBaseDO requireKnowledgeBase(Long kbId) {
        KnowledgeBaseDO entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("knowledge base not found");
        }
        return entity;
    }

    private KnowledgeDocumentDO requireDocument(Long docId) {
        KnowledgeDocumentDO entity = knowledgeDocumentMapper.selectById(docId);
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

