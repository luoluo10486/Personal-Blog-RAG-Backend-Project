package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeCollectionNameResolver {
    private final KnowledgeProperties knowledgeProperties;

    public KnowledgeCollectionNameResolver(KnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    public String resolve(String baseCode) {
        String normalizedBaseCode = normalize(baseCode);
        if (knowledgeProperties.getDefaultBaseCode().equals(normalizedBaseCode)) {
            return knowledgeProperties.getDefaults().getCollectionName();
        }
        return knowledgeProperties.getVector().getMilvus().getCollectionPrefix() + normalizedBaseCode;
    }

    private String normalize(String baseCode) {
        String fallback = knowledgeProperties.getDefaultBaseCode();
        if (baseCode == null || baseCode.isBlank()) {
            return fallback;
        }
        return baseCode.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
