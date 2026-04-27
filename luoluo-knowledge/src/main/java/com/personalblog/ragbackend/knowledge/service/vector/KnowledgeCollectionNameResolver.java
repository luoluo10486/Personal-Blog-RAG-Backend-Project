package com.personalblog.ragbackend.knowledge.service.vector;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeCollectionNameResolver {
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;

    public KnowledgeCollectionNameResolver(KnowledgeVectorSpaceResolver vectorSpaceResolver) {
        this.vectorSpaceResolver = vectorSpaceResolver;
    }

    public String resolve(String baseCode) {
        return vectorSpaceResolver.resolve(baseCode).collectionName();
    }
}
