package com.personalblog.ragbackend.ingestion.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.ingestion.domain.context.DocumentSource;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.domain.settings.IndexerSettings;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceId;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreAdmin;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class IndexerNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final VectorStoreAdmin vectorStoreAdmin;
    private final VectorStoreService vectorStoreService;
    private final RAGDefaultProperties ragDefaultProperties;

    public IndexerNode(ObjectMapper objectMapper,
                       VectorStoreAdmin vectorStoreAdmin,
                       VectorStoreService vectorStoreService,
                       RAGDefaultProperties ragDefaultProperties) {
        this.objectMapper = objectMapper;
        this.vectorStoreAdmin = vectorStoreAdmin;
        this.vectorStoreService = vectorStoreService;
        this.ragDefaultProperties = ragDefaultProperties;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.INDEXER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        List<VectorChunk> chunks = context.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return NodeResult.fail(new ClientException("no chunks to index"));
        }
        IndexerSettings settings = parseSettings(config.getSettings());
        String collectionName = resolveCollectionName(context);
        if (!StringUtils.hasText(collectionName)) {
            return NodeResult.fail(new ClientException("collection name is required"));
        }
        for (VectorChunk chunk : chunks) {
            if (chunk == null || chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                return NodeResult.fail(new ClientException("chunk embedding is required"));
            }
        }
        ensureVectorSpace(collectionName);
        if (!context.isSkipIndexerWrite()) {
            vectorStoreService.indexDocumentChunks(collectionName, context.getTaskId(), chunks);
        }
        return NodeResult.ok("indexed " + chunks.size() + " chunks");
    }

    private IndexerSettings parseSettings(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return IndexerSettings.builder().build();
        }
        return objectMapper.convertValue(node, IndexerSettings.class);
    }

    private String resolveCollectionName(IngestionContext context) {
        if (context.getVectorSpaceId() != null && StringUtils.hasText(context.getVectorSpaceId().getLogicalName())) {
            return context.getVectorSpaceId().getLogicalName();
        }
        return ragDefaultProperties.getCollectionName();
    }

    private void ensureVectorSpace(String collectionName) {
        boolean exists = vectorStoreAdmin.vectorSpaceExists(KnowledgeVectorSpaceId.builder()
                .logicalName(collectionName)
                .build());
        if (exists) {
            return;
        }
        vectorStoreAdmin.ensureVectorSpace(new KnowledgeVectorSpace(
                KnowledgeVectorSpaceId.builder().logicalName(collectionName).build(),
                collectionName,
                "pg",
                null,
                ragDefaultProperties.getDimension() == null ? 0 : ragDefaultProperties.getDimension()
        ));
    }
}
