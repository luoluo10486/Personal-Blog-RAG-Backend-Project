package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeVectorSpaceResolver {
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeVectorSpaceResolver(KnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    public KnowledgeVectorSpace resolve(String baseCode) {
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
