package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;

import java.util.List;

public interface VectorStoreService {

    void upsert(KnowledgeVectorSpace vectorSpace, List<KnowledgeVectorDocument> documents);

    void deleteByIds(KnowledgeVectorSpace vectorSpace, List<String> vectorIds);

    List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                 List<Float> queryVector,
                                 int topK,
                                 int candidateLimit);
}
