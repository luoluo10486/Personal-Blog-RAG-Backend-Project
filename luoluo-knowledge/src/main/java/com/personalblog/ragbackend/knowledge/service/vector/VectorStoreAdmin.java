package com.personalblog.ragbackend.knowledge.service.vector;

public interface VectorStoreAdmin {

    void ensureVectorSpace(KnowledgeVectorSpace vectorSpace);

    boolean vectorSpaceExists(KnowledgeVectorSpaceId spaceId, String collectionName);
}
