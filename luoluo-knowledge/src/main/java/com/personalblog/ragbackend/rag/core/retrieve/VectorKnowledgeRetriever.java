package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VectorKnowledgeRetriever implements KnowledgeCandidateRetriever {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final ObjectProvider<KnowledgeChunkMapper> knowledgeChunkMapperProvider;
    private final ObjectProvider<KnowledgeDocumentMapper> knowledgeDocumentMapperProvider;

    public VectorKnowledgeRetriever(KnowledgeProperties knowledgeProperties,
                                    KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                    ObjectProvider<EmbeddingService> embeddingServiceProvider,
                                    ObjectProvider<VectorStoreService> vectorStoreServiceProvider,
                                    ObjectProvider<KnowledgeChunkMapper> knowledgeChunkMapperProvider,
                                    ObjectProvider<KnowledgeDocumentMapper> knowledgeDocumentMapperProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.knowledgeChunkMapperProvider = knowledgeChunkMapperProvider;
        this.knowledgeDocumentMapperProvider = knowledgeDocumentMapperProvider;
    }

    @Override
    public String getName() {
        return "vector";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public boolean isEnabled(RetrieveRequest request) {
        return knowledgeProperties.isEnabled()
                && embeddingServiceProvider.getIfAvailable() != null
                && vectorStoreServiceProvider.getIfAvailable() != null
                && request.question() != null
                && !request.question().isBlank();
    }

    @Override
    @RagTraceNode(name = "vector-retriever", type = "RETRIEVE_CHANNEL")
    public List<RetrievedChunk> retrieveCandidates(RetrieveRequest request) {
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        if (embeddingService == null || vectorStoreService == null) {
            return List.of();
        }

        try {
            KnowledgeVectorSpace vectorSpace = vectorSpaceResolver.resolve(request.baseCode());
            int candidateLimit = Math.max(
                    request.topK(),
                    request.topK() * Math.max(knowledgeProperties.getSearch().getTopKMultiplier(), 1)
            );
            List<Float> queryVector = embeddingService.embed(request.question());
            List<VectorSearchHit> hits = vectorStoreService.search(vectorSpace, queryVector, request.topK(), candidateLimit);
            return hits.stream()
                    .filter(this::isHitEnabled)
                    .map(hit -> toRetrievedChunk(request.baseCode(), hit))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private RetrievedChunk toRetrievedChunk(String baseCode, VectorSearchHit hit) {
        Map<String, Object> metadata = hit.metadata();
        return RetrievedChunk.builder()
                .id(stringValue(metadata.getOrDefault("chunkId", hit.vectorId())))
                .text(hit.content())
                .score((float) hit.score())
                .build();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isHitEnabled(VectorSearchHit hit) {
        KnowledgeChunkMapper chunkMapper = knowledgeChunkMapperProvider.getIfAvailable();
        KnowledgeDocumentMapper documentMapper = knowledgeDocumentMapperProvider.getIfAvailable();
        if (chunkMapper == null || documentMapper == null) {
            return true;
        }
        Map<String, Object> metadata = hit.metadata();
        Long chunkId = parseLong(metadata.get("chunkId"));
        if (chunkId == null) {
            return true;
        }
        KnowledgeChunkEntity chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || chunk.getEnabled() == null || chunk.getEnabled() != 1) {
            return false;
        }
        KnowledgeDocumentEntity document = documentMapper.selectById(chunk.getDocId());
        return document != null && document.getEnabled() != null && document.getEnabled() == 1;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
