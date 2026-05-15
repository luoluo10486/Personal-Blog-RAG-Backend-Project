package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionPersistenceService;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreAdmin;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IndexIngestionNode implements KnowledgeIngestionNode {
    private final ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final ObjectProvider<KnowledgeIngestionPersistenceService> persistenceServiceProvider;

    public IndexIngestionNode(ObjectProvider<VectorStoreAdmin> vectorStoreAdminProvider,
                              ObjectProvider<VectorStoreService> vectorStoreServiceProvider,
                              ObjectProvider<KnowledgeIngestionPersistenceService> persistenceServiceProvider) {
        this.vectorStoreAdminProvider = vectorStoreAdminProvider;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.persistenceServiceProvider = persistenceServiceProvider;
    }

    @Override
    public String getNodeType() {
        return "index";
    }

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    @RagTraceNode(name = "index-ingestion", type = "INGEST_INDEX")
    public void execute(KnowledgeIngestionContext context) {
        if (!context.isIngestMode()) {
            return;
        }
        if (context.getIngestionSummary() != null && !context.getIngestionSummary().success()) {
            return;
        }
        if (context.getPlan() == null || context.getPersistedChunks().isEmpty()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Knowledge chunks were not persisted before indexing"));
            return;
        }
        if (context.getEmbeddings().isEmpty()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Chunk embeddings are empty"));
            return;
        }

        VectorStoreAdmin vectorStoreAdmin = vectorStoreAdminProvider.getIfAvailable();
        VectorStoreService vectorStoreService = vectorStoreServiceProvider.getIfAvailable();
        KnowledgeIngestionPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (vectorStoreAdmin == null || vectorStoreService == null || persistenceService == null) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Vector indexing service is unavailable"));
            return;
        }

        try {
            vectorStoreAdmin.ensureVectorSpace(context.getPlan().vectorSpace());
            vectorStoreService.deleteByIds(context.getPlan().vectorSpace(), context.getStaleVectorIds());
            vectorStoreService.upsert(
                    context.getPlan().vectorSpace(),
                    buildVectorDocuments(context)
            );
            persistenceService.saveVectorRefs(context);
            persistenceService.markDocumentIndexed(context.getDocumentId(), context.getPersistedChunks().size());
            context.setVectorIndexed(true);
        } catch (RuntimeException exception) {
            persistenceService.markDocumentFailed(context.getDocumentId());
            context.setIngestionSummary(DocumentIngestionSummary.failure(
                    context.getBaseCode(),
                    "Vector indexing failed: " + exception.getMessage()
            ));
        }
    }

    private List<KnowledgeVectorDocument> buildVectorDocuments(KnowledgeIngestionContext context) {
        List<KnowledgeChunkEntity> persistedChunks = context.getPersistedChunks();
        List<DocumentChunk> chunks = context.getChunks();
        List<List<Float>> embeddings = context.getEmbeddings();
        return java.util.stream.IntStream.range(0, persistedChunks.size())
                .mapToObj(index -> toVectorDocument(context, persistedChunks.get(index), chunks.get(index), embeddings.get(index)))
                .toList();
    }

    private KnowledgeVectorDocument toVectorDocument(KnowledgeIngestionContext context,
                                                     KnowledgeChunkEntity chunkEntity,
                                                     DocumentChunk chunk,
                                                     List<Float> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String chunkId = String.valueOf(chunkEntity.getId());
        String documentId = context.getDocumentId() == null ? "" : String.valueOf(context.getDocumentId());
        String knowledgeBaseId = context.getKnowledgeBaseId() == null ? "" : String.valueOf(context.getKnowledgeBaseId());
        String collectionName = context.getPlan() == null || context.getPlan().vectorSpace() == null
                ? ""
                : context.getPlan().vectorSpace().collectionName();
        metadata.put("chunkId", chunkId);
        metadata.put("chunk_id", chunkId);
        metadata.put("documentId", documentId);
        metadata.put("docId", documentId);
        metadata.put("doc_id", documentId);
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("kbId", knowledgeBaseId);
        metadata.put("kb_id", knowledgeBaseId);
        metadata.put("baseCode", context.getPlan().baseCode());
        metadata.put("collectionName", collectionName);
        metadata.put("collection_name", collectionName);
        metadata.put("title", context.getFile() == null ? "" : context.getFile().getOriginalFilename());
        metadata.put("sourceUrl", "");
        metadata.put("chunkIndex", chunk.chunkIndex());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.put("sectionTitle", chunk.sectionTitle());
        return new KnowledgeVectorDocument(
                chunkId,
                chunk.content(),
                embedding,
                metadata
        );
    }
}
