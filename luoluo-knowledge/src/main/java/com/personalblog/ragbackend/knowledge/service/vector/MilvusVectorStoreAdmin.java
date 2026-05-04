package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnBean(MilvusClientV2.class)
@ConditionalOnProperty(prefix = "app.knowledge.vector", name = "type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreAdmin implements VectorStoreAdmin {
    private final MilvusClientV2 milvusClient;
    private final KnowledgeProperties knowledgeProperties;

    public MilvusVectorStoreAdmin(MilvusClientV2 milvusClient, KnowledgeProperties knowledgeProperties) {
        this.milvusClient = milvusClient;
        this.knowledgeProperties = knowledgeProperties;
    }

    @Override
    public void ensureVectorSpace(KnowledgeVectorSpace vectorSpace) {
        if (vectorSpaceExists(vectorSpace.spaceId(), vectorSpace.collectionName())) {
            return;
        }

        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("id")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("content")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("metadata")
                .dataType(DataType.JSON)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name("embedding")
                .dataType(DataType.FloatVector)
                .dimension(vectorSpace.dimension())
                .build());

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fields)
                .build();

        IndexParam hnswIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(resolveMetricType())
                .indexName("embedding")
                .extraParams(Map.of(
                        "M", "32",
                        "efConstruction", "200"
                ))
                .build();

        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(vectorSpace.collectionName())
                .collectionSchema(schema)
                .primaryFieldName("id")
                .vectorFieldName("embedding")
                .metricType(resolveMetricType().name())
                .consistencyLevel(resolveConsistencyLevel())
                .indexParams(List.of(hnswIndex))
                .description("Knowledge vector space for " + vectorSpace.collectionName());
        applyDatabase(builder);
        milvusClient.createCollection(builder.build());
    }

    @Override
    public boolean vectorSpaceExists(KnowledgeVectorSpaceId spaceId, String collectionName) {
        HasCollectionReq.HasCollectionReqBuilder builder = HasCollectionReq.builder()
                .collectionName(collectionName);
        applyDatabase(builder);
        return milvusClient.hasCollection(builder.build());
    }

    private IndexParam.MetricType resolveMetricType() {
        String metricType = knowledgeProperties.getDefaults().getMetricType();
        if (metricType == null || metricType.isBlank()) {
            return IndexParam.MetricType.COSINE;
        }
        return IndexParam.MetricType.valueOf(metricType.trim().toUpperCase());
    }

    private ConsistencyLevel resolveConsistencyLevel() {
        String consistencyLevel = knowledgeProperties.getVector().getMilvus().getConsistencyLevel();
        if (consistencyLevel == null || consistencyLevel.isBlank()) {
            return ConsistencyLevel.BOUNDED;
        }
        return ConsistencyLevel.valueOf(consistencyLevel.trim().toUpperCase());
    }

    private void applyDatabase(Object builder) {
        String databaseName = knowledgeProperties.getVector().getMilvus().getDatabaseName();
        if (databaseName == null || databaseName.isBlank()) {
            return;
        }
        String normalized = databaseName.trim();
        if (builder instanceof HasCollectionReq.HasCollectionReqBuilder hasCollectionBuilder) {
            hasCollectionBuilder.databaseName(normalized);
        } else if (builder instanceof CreateCollectionReq.CreateCollectionReqBuilder createCollectionBuilder) {
            createCollectionBuilder.databaseName(normalized);
        }
    }
}
