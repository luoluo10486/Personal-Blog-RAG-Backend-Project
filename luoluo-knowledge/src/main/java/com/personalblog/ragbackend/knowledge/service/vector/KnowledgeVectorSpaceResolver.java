package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeVectorSpaceResolver {
    private final KnowledgeProperties knowledgeProperties;
    private final ObjectProvider<KnowledgeBaseMapper> knowledgeBaseMapperProvider;

    public KnowledgeVectorSpaceResolver(KnowledgeProperties knowledgeProperties) {
        this(knowledgeProperties, null);
    }

    @Autowired
    public KnowledgeVectorSpaceResolver(KnowledgeProperties knowledgeProperties,
                                        ObjectProvider<KnowledgeBaseMapper> knowledgeBaseMapperProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeBaseMapperProvider = knowledgeBaseMapperProvider;
    }

    public KnowledgeVectorSpace resolve(String baseCode) {
        KnowledgeBaseEntity knowledgeBase = findKnowledgeBase(baseCode);
        if (knowledgeBase != null) {
            return new KnowledgeVectorSpace(
                    new KnowledgeVectorSpaceId(String.valueOf(knowledgeBase.getId()), resolveNamespace()),
                    knowledgeBase.getCollectionName(),
                    knowledgeProperties.getVector().getType(),
                    StringUtils.hasText(knowledgeBase.getEmbeddingModel())
                            ? knowledgeBase.getEmbeddingModel().trim()
                            : knowledgeProperties.getDefaults().getEmbeddingModel(),
                    knowledgeProperties.getDefaults().getDimension()
            );
        }

        String normalizedBaseCode = normalizeBaseCode(baseCode);
        return new KnowledgeVectorSpace(
                new KnowledgeVectorSpaceId(normalizedBaseCode, resolveNamespace()),
                resolveCollectionName(normalizedBaseCode),
                knowledgeProperties.getVector().getType(),
                knowledgeProperties.getDefaults().getEmbeddingModel(),
                knowledgeProperties.getDefaults().getDimension()
        );
    }

    public String normalizeBaseCode(String baseCode) {
        String fallback = knowledgeProperties.getDefaultBaseCode();
        if (baseCode == null || baseCode.isBlank()) {
            return fallback;
        }
        return baseCode.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String resolveCollectionName(String normalizedBaseCode) {
        if (knowledgeProperties.getDefaultBaseCode().equals(normalizedBaseCode)) {
            return knowledgeProperties.getDefaults().getCollectionName();
        }
        return knowledgeProperties.getVector().getMilvus().getCollectionPrefix() + normalizedBaseCode;
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
        String vectorType = knowledgeProperties.getVector().getType();
        if (vectorType == null || vectorType.isBlank()) {
            return "";
        }
        if ("milvus".equalsIgnoreCase(vectorType)) {
            return blankToEmpty(knowledgeProperties.getVector().getMilvus().getDatabaseName());
        }
        if ("pgvector".equalsIgnoreCase(vectorType) || "pg".equalsIgnoreCase(vectorType)) {
            return blankToEmpty(knowledgeProperties.getVector().getPg().getSchema());
        }
        return "";
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
