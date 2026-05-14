package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface VectorStoreService {

    void upsert(KnowledgeVectorSpace vectorSpace, List<KnowledgeVectorDocument> documents);

    void deleteByIds(KnowledgeVectorSpace vectorSpace, List<String> vectorIds);

    List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                 List<Float> queryVector,
                                 int topK,
                                 int candidateLimit);

    default void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<KnowledgeVectorDocument> documents = new ArrayList<>(chunks.size());
        for (VectorChunk chunk : chunks) {
            documents.add(new KnowledgeVectorDocument(
                    chunk.getChunkId(),
                    chunk.getContent(),
                    toList(chunk.getEmbedding()),
                    chunk.getMetadata() == null ? Map.of() : chunk.getMetadata()
            ));
        }
        upsert(new KnowledgeVectorSpace(
                new KnowledgeVectorSpaceId(docId, "public"),
                collectionName,
                "pg",
                null,
                chunks.get(0).getEmbedding() == null ? 0 : chunks.get(0).getEmbedding().length
        ), documents);
    }

    default void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        indexDocumentChunks(collectionName, docId, List.of(chunk));
    }

    default void deleteDocumentVectors(String collectionName, String docId) {
        // 默认空实现，具体实现类按数据库模型覆写
    }

    default void deleteChunkById(String collectionName, String chunkId) {
        deleteChunksByIds(collectionName, List.of(chunkId));
    }

    default void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        // 默认空实现，具体实现类按数据库模型覆写
    }

    private static List<Float> toList(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        List<Float> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add(value);
        }
        return values;
    }
}
