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
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkDO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentDO;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeVectorRefMapper;
import com.personalblog.ragbackend.knowledge.domain.enums.DocumentStatus;
import com.personalblog.ragbackend.knowledge.service.KnowledgeChunkService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeVectorRefDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final TokenCounterService tokenCounterService;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeChunkServiceImpl(KnowledgeChunkMapper knowledgeChunkMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                     VectorStoreService vectorStoreService,
                                     EmbeddingService embeddingService,
                                     TokenCounterService tokenCounterService,
                                     KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                                     ObjectMapper objectMapper) {
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.tokenCounterService = tokenCounterService;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        Long documentId = parseId(docId);
        requireDocument(documentId);
        IPage<KnowledgeChunkDO> page = knowledgeChunkMapper.selectPage(
                new Page<>(requestParam.getCurrent(), requestParam.getSize()),
                new LambdaQueryWrapper<KnowledgeChunkDO>()
                        .eq(KnowledgeChunkDO::getDocId, documentId)
                        .eq(requestParam.getEnabled() != null, KnowledgeChunkDO::getEnabled, requestParam.getEnabled())
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex)
        );
        return page.convert(this::toView);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam) {
        KnowledgeDocumentDO document = requireActiveDocument(docId, true);
        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        KnowledgeChunkDO entity = persistChunk(document, kbDO, requestParam, resolveNextChunkIndex(docId), true);
        return toView(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams) {
        batchCreate(docId, requestParams, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams, boolean writeVector) {
        if (requestParams == null || requestParams.isEmpty()) {
            return;
        }

        KnowledgeDocumentDO document = requireActiveDocument(docId, true);
        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        boolean needAutoIndex = requestParams.stream().anyMatch(request -> request.getIndex() == null);
        int nextIndex = needAutoIndex ? resolveNextChunkIndex(docId) : 0;
        List<KnowledgeChunkDO> chunks = new ArrayList<>(requestParams.size());
        for (KnowledgeChunkCreateRequest request : requestParams) {
            Integer index = request.getIndex() != null ? request.getIndex() : nextIndex++;
            chunks.add(persistChunk(document, kbDO, request, index, false));
        }
        if (writeVector) {
            syncChunksToVector(document, kbDO, chunks);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam) {
        KnowledgeDocumentDO document = requireActiveDocument(docId, false);
        KnowledgeChunkDO entity = requireChunk(parseId(docId), parseId(chunkId));
        String newContent = requireContent(requestParam.getContent());
        if (newContent.equals(entity.getContent())) {
            return;
        }

        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        entity.setContent(newContent);
        entity.setContentHash(sha256Hex(newContent));
        entity.setCharCount(newContent.length());
        entity.setTokenCount(resolveTokenCount(newContent));
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeChunkMapper.updateById(entity);
        syncChunkToVector(kbDO, document, entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId, String chunkId) {
        KnowledgeDocumentDO document = requireActiveDocument(docId, false);
        KnowledgeChunkDO chunk = requireChunk(parseId(docId), parseId(chunkId));
        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        knowledgeChunkMapper.deleteById(parseId(chunkId));
        knowledgeDocumentMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeDocumentDO>()
                        .setSql("chunk_count = CASE WHEN chunk_count > 0 THEN chunk_count - 1 ELSE 0 END")
                        .eq(KnowledgeDocumentDO::getId, parseId(docId))
        );
        deleteVector(kbDO, String.valueOf(chunk.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        KnowledgeDocumentDO document = requireActiveDocument(docId, true);
        KnowledgeChunkDO entity = requireChunk(parseId(docId), parseId(chunkId));
        int enabledValue = enabled ? 1 : 0;
        if (entity.getEnabled() != null && entity.getEnabled() == enabledValue) {
            return;
        }

        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        entity.setEnabled(enabledValue);
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeChunkMapper.updateById(entity);
        if (enabled) {
            syncChunkToVector(kbDO, document, entity);
        } else {
            deleteVector(kbDO, String.valueOf(entity.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        if (requestParam == null || requestParam.getChunkIds() == null || requestParam.getChunkIds().isEmpty()) {
            throw new IllegalArgumentException("chunk ids are required");
        }

        KnowledgeDocumentDO document = requireActiveDocument(docId, true);
        KnowledgeBaseDO kbDO = requireKnowledgeBase(document.getKbId());
        List<Long> chunkIds = requestParam.getChunkIds().stream().map(this::parseId).toList();
        List<KnowledgeChunkDO> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, parseId(docId))
                .in(KnowledgeChunkDO::getId, chunkIds));
        for (KnowledgeChunkDO chunk : chunks) {
            chunk.setEnabled(enabled ? 1 : 0);
            chunk.setUpdatedBy(parseUserId(UserContext.getUserId()));
            knowledgeChunkMapper.updateById(chunk);
        }
        if (enabled) {
            syncChunksToVector(document, kbDO, chunks);
        } else {
            List<String> vectorIds = chunks.stream().map(chunk -> String.valueOf(chunk.getId())).toList();
            vectorStoreService.deleteChunksByIds(kbDO.getCollectionName(), vectorIds);
            knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefDO>()
                    .eq(KnowledgeVectorRefDO::getDocId, document.getId())
                    .in(KnowledgeVectorRefDO::getChunkId, chunks.stream().map(KnowledgeChunkDO::getId).toList()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledByDocId(String docId, String kbId, boolean enabled) {
        Long documentId = parseId(docId);
        int enabledValue = enabled ? 1 : 0;
        knowledgeChunkMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeChunkDO>()
                        .eq(KnowledgeChunkDO::getDocId, documentId)
                        .set(KnowledgeChunkDO::getEnabled, enabledValue)
                        .set(KnowledgeChunkDO::getUpdatedBy, parseUserId(UserContext.getUserId()))
        );
    }

    @Override
    public List<KnowledgeChunkVO> listByDocId(String docId) {
        Long documentId = parseId(docId);
        requireDocument(documentId);
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkDO>()
                        .eq(KnowledgeChunkDO::getDocId, documentId)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(String docId) {
        Long documentId = parseId(docId);
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, documentId));
    }

    private KnowledgeChunkDO persistChunk(KnowledgeDocumentDO document,
                                              KnowledgeBaseDO kbDO,
                                              KnowledgeChunkCreateRequest requestParam,
                                              int chunkIndex,
                                              boolean writeVector) {
        KnowledgeChunkDO entity = new KnowledgeChunkDO();
        Long explicitId = tryParseLong(requestParam.getChunkId());
        if (explicitId != null) {
            entity.setId(explicitId);
        }
        String content = requireContent(requestParam.getContent());
        entity.setKbId(document.getKbId());
        entity.setDocId(document.getId());
        entity.setChunkIndex(requestParam.getIndex() == null ? chunkIndex : requestParam.getIndex());
        entity.setContent(content);
        entity.setContentHash(sha256Hex(content));
        entity.setCharCount(content.length());
        entity.setTokenCount(resolveTokenCount(content));
        entity.setEnabled(1);
        entity.setCreatedBy(parseUserId(UserContext.getUserId()));
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeChunkMapper.insert(entity);
        knowledgeDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocumentDO>()
                        .setSql("chunk_count = chunk_count + 1")
                        .eq(KnowledgeDocumentDO::getId, document.getId())
        );
        if (writeVector) {
            syncChunkToVector(kbDO, document, entity);
        }
        return entity;
    }

    private void syncChunksToVector(KnowledgeDocumentDO document,
                                    KnowledgeBaseDO kbDO,
                                    List<KnowledgeChunkDO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<List<Float>> embeddings = embeddingService.embedBatch(chunks.stream().map(KnowledgeChunkDO::getContent).toList());
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("embedding result size mismatch");
        }
        List<VectorChunk> vectorChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunkDO chunk = chunks.get(i);
            vectorChunks.add(VectorChunk.builder()
                    .chunkId(String.valueOf(chunk.getId()))
                    .content(chunk.getContent())
                    .metadata(buildVectorMetadata(document, kbDO, chunk))
                    .embedding(toArray(embeddings.get(i)))
                    .index(chunk.getChunkIndex())
                    .build());
        }
        vectorStoreService.indexDocumentChunks(kbDO.getCollectionName(), String.valueOf(document.getId()), vectorChunks);
        for (KnowledgeChunkDO chunk : chunks) {
            upsertVectorRef(document, kbDO, chunk);
        }
    }

    private void syncChunkToVector(KnowledgeBaseDO kbDO,
                                   KnowledgeDocumentDO document,
                                   KnowledgeChunkDO chunk) {
        VectorChunk vectorChunk = VectorChunk.builder()
                .chunkId(String.valueOf(chunk.getId()))
                .content(chunk.getContent())
                .metadata(buildVectorMetadata(document, kbDO, chunk))
                .embedding(toArray(embeddingService.embed(chunk.getContent())))
                .index(chunk.getChunkIndex())
                .build();
        vectorStoreService.updateChunk(kbDO.getCollectionName(), String.valueOf(document.getId()), vectorChunk);
        upsertVectorRef(document, kbDO, chunk);
    }

    private void deleteVector(KnowledgeBaseDO kbDO, String vectorId) {
        vectorStoreService.deleteChunkById(kbDO.getCollectionName(), vectorId);
        Long chunkId = tryParseLong(vectorId);
        if (chunkId != null) {
            knowledgeVectorRefMapper.delete(new LambdaQueryWrapper<KnowledgeVectorRefDO>()
                    .eq(KnowledgeVectorRefDO::getChunkId, chunkId));
        }
    }

    private void upsertVectorRef(KnowledgeDocumentDO document,
                                 KnowledgeBaseDO kbDO,
                                 KnowledgeChunkDO chunk) {
        KnowledgeVectorRefDO ref = knowledgeVectorRefMapper.selectOne(new LambdaQueryWrapper<KnowledgeVectorRefDO>()
                .eq(KnowledgeVectorRefDO::getChunkId, chunk.getId())
                .last("LIMIT 1"));
        if (ref == null) {
            ref = new KnowledgeVectorRefDO();
            ref.setChunkId(chunk.getId());
            ref.setCreatedBy(parseUserId(UserContext.getUserId()));
            ref.setDeleted(0);
        }
        ref.setKbId(document.getKbId());
        ref.setDocId(document.getId());
        ref.setCollectionName(kbDO.getCollectionName());
        ref.setVectorId(String.valueOf(chunk.getId()));
        ref.setEmbeddingModel(kbDO.getEmbeddingModel());
        ref.setEmbeddingDim(vectorSpaceResolver.resolve(String.valueOf(kbDO.getId())).dimension());
        ref.setMetadata(toJson(buildVectorMetadata(document, kbDO, chunk)));
        if (ref.getId() == null) {
            knowledgeVectorRefMapper.insert(ref);
        } else {
            knowledgeVectorRefMapper.updateById(ref);
        }
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentDO document,
                                                    KnowledgeBaseDO kbDO,
                                                    KnowledgeChunkDO chunk) {
        Map<String, Object> metadata = new HashMap<>();
        String chunkId = String.valueOf(chunk.getId());
        String documentId = String.valueOf(document.getId());
        String knowledgeBaseId = String.valueOf(document.getKbId());
        String collectionName = kbDO.getCollectionName();
        metadata.put("chunkId", chunkId);
        metadata.put("chunk_id", chunkId);
        metadata.put("documentId", documentId);
        metadata.put("docId", documentId);
        metadata.put("doc_id", documentId);
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("kbId", knowledgeBaseId);
        metadata.put("kb_id", knowledgeBaseId);
        metadata.put("baseCode", collectionName);
        metadata.put("collectionName", collectionName);
        metadata.put("collection_name", collectionName);
        metadata.put("title", document.getDocName());
        metadata.put("sourceUrl", document.getFileUrl());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("chunk_index", chunk.getChunkIndex());
        metadata.put("sectionTitle", "");
        return metadata;
    }

    private Integer resolveTokenCount(String content) {
        Integer tokenCount = tokenCounterService.countTokens(content);
        return tokenCount == null ? 0 : tokenCount;
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

    private KnowledgeDocumentDO requireActiveDocument(String docId, boolean requireEnabled) {
        KnowledgeDocumentDO entity = knowledgeDocumentMapper.selectById(parseId(docId));
        if (entity == null) {
            throw new IllegalArgumentException("document not found");
        }
        if (DocumentStatus.RUNNING.getCode().equalsIgnoreCase(entity.getStatus())) {
            throw new IllegalArgumentException("document is already running");
        }
        if (requireEnabled && entity.getEnabled() != null && entity.getEnabled() != 1) {
            throw new IllegalArgumentException("document is not enabled");
        }
        return entity;
    }

    private Integer resolveNextChunkIndex(String docId) {
        KnowledgeChunkDO latest = knowledgeChunkMapper.selectOne(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, parseId(docId))
                .orderByDesc(KnowledgeChunkDO::getChunkIndex)
                .last("LIMIT 1"));
        return latest == null || latest.getChunkIndex() == null ? 0 : latest.getChunkIndex() + 1;
    }

    private String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("chunk content is required");
        }
        return content.trim();
    }

    private String parseUserName() {
        return StringUtils.hasText(UserContext.getUsername()) ? UserContext.getUsername() : "system";
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

    private Long tryParseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize metadata", exception);
        }
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                String hex = Integer.toHexString(Byte.toUnsignedInt(value));
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash chunk content", exception);
        }
    }

    private KnowledgeChunkVO toView(KnowledgeChunkDO chunk) {
        KnowledgeChunkVO vo = BeanUtil.toBean(chunk, KnowledgeChunkVO.class);
        vo.setId(String.valueOf(chunk.getId()));
        vo.setKbId(String.valueOf(chunk.getKbId()));
        vo.setDocId(String.valueOf(chunk.getDocId()));
        vo.setCreateTime(chunk.getCreatedAt());
        vo.setUpdateTime(chunk.getUpdatedAt());
        return vo;
    }

    private KnowledgeChunkDO requireChunk(Long documentId, Long chunkId) {
        KnowledgeChunkDO entity = knowledgeChunkMapper.selectById(chunkId);
        if (entity == null || !Objects.equals(entity.getDocId(), documentId)) {
            throw new IllegalArgumentException("chunk not found");
        }
        return entity;
    }

    private KnowledgeDocumentDO requireDocument(Long documentId) {
        KnowledgeDocumentDO entity = knowledgeDocumentMapper.selectById(documentId);
        if (entity == null) {
            throw new IllegalArgumentException("document not found");
        }
        return entity;
    }

    private KnowledgeBaseDO requireKnowledgeBase(Long kbId) {
        KnowledgeBaseDO entity = knowledgeBaseMapper.selectById(kbId);
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

