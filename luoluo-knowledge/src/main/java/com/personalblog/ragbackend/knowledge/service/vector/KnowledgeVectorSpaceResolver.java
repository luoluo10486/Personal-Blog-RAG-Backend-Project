package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeVectorSpaceResolver {
    private final RAGDefaultProperties ragDefaultProperties;
    private final SearchChannelProperties searchChannelProperties;
    private final ObjectProvider<KnowledgeBaseMapper> knowledgeBaseMapperProvider;

    public KnowledgeVectorSpaceResolver(RAGDefaultProperties ragDefaultProperties,
                                        SearchChannelProperties searchChannelProperties) {
        this(ragDefaultProperties, searchChannelProperties, null);
    }

    @Autowired
    public KnowledgeVectorSpaceResolver(RAGDefaultProperties ragDefaultProperties,
                                        SearchChannelProperties searchChannelProperties,
                                        ObjectProvider<KnowledgeBaseMapper> knowledgeBaseMapperProvider) {
        this.ragDefaultProperties = ragDefaultProperties;
        this.searchChannelProperties = searchChannelProperties;
        this.knowledgeBaseMapperProvider = knowledgeBaseMapperProvider;
    }

    public KnowledgeVectorSpace resolve(String baseCode) {
        KnowledgeBaseEntity knowledgeBase = findKnowledgeBase(baseCode);
        if (knowledgeBase != null) {
            return new KnowledgeVectorSpace(
                    new KnowledgeVectorSpaceId(String.valueOf(knowledgeBase.getId()), resolveNamespace()),
                    knowledgeBase.getCollectionName(),
                    resolveVectorType(),
                    StringUtils.hasText(knowledgeBase.getEmbeddingModel())
                            ? knowledgeBase.getEmbeddingModel().trim()
                            : "Qwen/Qwen3-Embedding-8B",
                    ragDefaultProperties.getDimension()
            );
        }

        String normalizedBaseCode = normalizeBaseCode(baseCode);
        return new KnowledgeVectorSpace(
                new KnowledgeVectorSpaceId(normalizedBaseCode, resolveNamespace()),
                resolveCollectionName(normalizedBaseCode),
                resolveVectorType(),
                "Qwen/Qwen3-Embedding-8B",
                ragDefaultProperties.getDimension()
        );
    }

    public String normalizeBaseCode(String baseCode) {
        String fallback = ragDefaultProperties.getCollectionName();
        if (baseCode == null || baseCode.isBlank()) {
            return fallback;
        }
        return baseCode.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String resolveCollectionName(String normalizedBaseCode) {
        if (ragDefaultProperties.getCollectionName() != null && ragDefaultProperties.getCollectionName().equals(normalizedBaseCode)) {
            return ragDefaultProperties.getCollectionName();
        }
        return "kb_" + normalizedBaseCode;
    }

    private KnowledgeBaseEntity findKnowledgeBase(String baseCode) {
        KnowledgeBaseMapper mapper = knowledgeBaseMapperProvider == null ? null : knowledgeBaseMapperProvider.getIfAvailable();
        if (mapper == null || !StringUtils.hasText(baseCode)) {
            return null;
        }

        String candidate = baseCode.trim();
        try {
            long id = Long.parseLong(candidate);
            KnowledgeBaseEntity byId = mapper.selectById(id);
            if (byId != null) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
        }

        return mapper.selectList(null).stream()
                .filter(entity -> matchesKnowledgeBase(entity, candidate))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesKnowledgeBase(KnowledgeBaseEntity entity, String candidate) {
        if (entity == null) {
            return false;
        }
        if (candidate.equals(entity.getCollectionName())) {
            return true;
        }
        if (candidate.equals(entity.getName())) {
            return true;
        }
        return normalizeBaseCode(candidate).equals(normalizeBaseCode(entity.getName()));
    }

    private String resolveNamespace() {
        String vectorType = resolveVectorType();
        if (vectorType == null || vectorType.isBlank()) {
            return "";
        }
        if ("milvus".equalsIgnoreCase(vectorType)) {
            return "default";
        }
        if ("pg".equalsIgnoreCase(vectorType)) {
            return "public";
        }
        return "";
    }

    private String resolveVectorType() {
        return "pg";
    }
}
