package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.infra.ai.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;
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

    public VectorKnowledgeRetriever(KnowledgeProperties knowledgeProperties,
                                    KnowledgeVectorSpaceResolver vectorSpaceResolver,
                                    ObjectProvider<EmbeddingService> embeddingServiceProvider,
                                    ObjectProvider<VectorStoreService> vectorStoreServiceProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
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
    public List<KnowledgeChunk> retrieveCandidates(RetrieveRequest request) {
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
                    .map(hit -> toKnowledgeChunk(request.baseCode(), hit))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private KnowledgeChunk toKnowledgeChunk(String baseCode, VectorSearchHit hit) {
        Map<String, Object> metadata = hit.metadata();
        return new KnowledgeChunk(
                stringValue(metadata.getOrDefault("chunkId", hit.vectorId())),
                normalizeBaseCode(baseCode, metadata.get("baseCode")),
                stringValue(metadata.getOrDefault("documentId", "")),
                stringValue(metadata.getOrDefault("title", "")),
                stringValue(metadata.getOrDefault("sourceUrl", "")),
                integerValue(metadata.get("chunkIndex")),
                hit.content(),
                hit.score()
        );
    }

    private String normalizeBaseCode(String fallback, Object metadataValue) {
        if (metadataValue == null) {
            return vectorSpaceResolver.normalizeBaseCode(fallback);
        }
        return stringValue(metadataValue);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
