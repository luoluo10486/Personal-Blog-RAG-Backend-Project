package com.personalblog.ragbackend.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.personalblog.ragbackend.rag.config.RagProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Milvus 的向量存储服务。
 */
@Service
@ConditionalOnProperty(prefix = "app.rag.milvus", name = "enabled", havingValue = "true")
public class MilvusVectorStoreService {
    private static final String ID_FIELD = "chunk_id";
    private static final String CONTENT_FIELD = "content";
    private static final String DOC_ID_FIELD = "doc_id";
    private static final String TITLE_FIELD = "title";
    private static final String CATEGORY_FIELD = "category";
    private static final String VECTOR_FIELD = "vector";
    private static final int CONTENT_MAX_LENGTH = 8192;
    private static final int DOC_ID_MAX_LENGTH = 128;
    private static final int TITLE_MAX_LENGTH = 256;
    private static final int CATEGORY_MAX_LENGTH = 128;
    private static final String VECTOR_INDEX_NAME = "idx_vector_hnsw";
    private static final String CATEGORY_INDEX_NAME = "idx_category_trie";
    private static final long DEFAULT_TIMEOUT_MS = 60000L;

    private final MilvusClientV2 milvusClient;
    private final RagProperties ragProperties;

    public MilvusVectorStoreService(MilvusClientV2 milvusClient, RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.ragProperties = ragProperties;
    }

    public PreparedCollection prepareCollection(int dimension) {
        String collectionName = resolveCollectionName(dimension);
        boolean created = false;
        if (!hasCollection(collectionName)) {
            createCollection(collectionName, dimension);
            createIndexes(collectionName);
            created = true;
        }
        loadCollection(collectionName);
        return new PreparedCollection(collectionName, created);
    }

    public void upsert(String collectionName, List<ChunkDocument> documents, List<double[]> vectors) {
        List<JsonObject> rows = new ArrayList<>(documents.size());
        for (int index = 0; index < documents.size(); index++) {
            ChunkDocument document = documents.get(index);
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, document.id());
            row.addProperty(CONTENT_FIELD, document.content());
            row.addProperty(DOC_ID_FIELD, document.docId());
            row.addProperty(TITLE_FIELD, document.title());
            row.addProperty(CATEGORY_FIELD, document.category());

            JsonArray vectorArray = new JsonArray();
            for (double value : vectors.get(index)) {
                vectorArray.add(value);
            }
            row.add(VECTOR_FIELD, vectorArray);
            rows.add(row);
        }

        milvusClient.upsert(UpsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build());
    }

    public void insert(String collectionName, List<ChunkDocument> documents, List<double[]> vectors) {
        List<JsonObject> rows = new ArrayList<>(documents.size());
        for (int index = 0; index < documents.size(); index++) {
            ChunkDocument document = documents.get(index);
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, document.id());
            row.addProperty(CONTENT_FIELD, document.content());
            row.addProperty(DOC_ID_FIELD, document.docId());
            row.addProperty(TITLE_FIELD, document.title());
            row.addProperty(CATEGORY_FIELD, document.category());

            JsonArray vectorArray = new JsonArray();
            for (double value : vectors.get(index)) {
                vectorArray.add(value);
            }
            row.add(VECTOR_FIELD, vectorArray);
            rows.add(row);
        }

        milvusClient.insert(InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build());
        flushCollection(collectionName);
    }

    public boolean isCollectionEmpty(String collectionName) {
        GetCollectionStatsReq.GetCollectionStatsReqBuilder builder = GetCollectionStatsReq.builder()
                .collectionName(collectionName);
        appendDatabase(builder);
        Long numOfEntities = milvusClient.getCollectionStats(builder.build()).getNumOfEntities();
        return numOfEntities == null || numOfEntities == 0L;
    }

    public List<SearchHit> search(String collectionName, double[] queryVector, int topK) {
        SearchResp response = milvusClient.search(SearchReq.builder()
                .collectionName(collectionName)
                .annsField(VECTOR_FIELD)
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of(CONTENT_FIELD, DOC_ID_FIELD, TITLE_FIELD, CATEGORY_FIELD))
                .data(List.of(new FloatVec(toFloatArray(queryVector))))
                .build());

        List<SearchHit> hits = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return hits;
        }

        int rank = 1;
        for (SearchResp.SearchResult result : searchResults.get(0)) {
            Map<String, Object> entity = result.getEntity();
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(DOC_ID_FIELD, asString(entity.get(DOC_ID_FIELD)));
            metadata.put(TITLE_FIELD, asString(entity.get(TITLE_FIELD)));
            metadata.put(CATEGORY_FIELD, asString(entity.get(CATEGORY_FIELD)));
            hits.add(new SearchHit(
                    rank++,
                    result.getScore(),
                    asString(entity.get(CONTENT_FIELD)),
                    metadata
            ));
        }
        return hits;
    }

    private boolean hasCollection(String collectionName) {
        HasCollectionReq.HasCollectionReqBuilder builder = HasCollectionReq.builder()
                .collectionName(collectionName);
        appendDatabase(builder);
        return milvusClient.hasCollection(builder.build());
    }

    private void createCollection(String collectionName, int dimension) {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema()
                .addField(AddFieldReq.builder()
                        .fieldName(ID_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(DOC_ID_MAX_LENGTH)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(CONTENT_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(CONTENT_MAX_LENGTH)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(DOC_ID_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(DOC_ID_MAX_LENGTH)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(TITLE_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(TITLE_MAX_LENGTH)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(CATEGORY_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(CATEGORY_MAX_LENGTH)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(dimension)
                        .build());

        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .description("Demo RAG chunks stored in Milvus")
                .collectionSchema(schema);
        appendDatabase(builder);
        milvusClient.createCollection(builder.build());
    }

    private void createIndexes(String collectionName) {
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexName(VECTOR_INDEX_NAME)
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of(
                        "M", 16,
                        "efConstruction", 256
                ))
                .build();

        IndexParam categoryIndex = IndexParam.builder()
                .fieldName(CATEGORY_FIELD)
                .indexName(CATEGORY_INDEX_NAME)
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        CreateIndexReq.CreateIndexReqBuilder builder = CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(List.of(vectorIndex, categoryIndex))
                .sync(true)
                .timeout(DEFAULT_TIMEOUT_MS);
        appendDatabase(builder);
        milvusClient.createIndex(builder.build());
    }

    private void loadCollection(String collectionName) {
        LoadCollectionReq.LoadCollectionReqBuilder builder = LoadCollectionReq.builder()
                .collectionName(collectionName)
                .sync(true)
                .timeout(DEFAULT_TIMEOUT_MS);
        appendDatabase(builder);
        milvusClient.loadCollection(builder.build());
    }

    private void flushCollection(String collectionName) {
        FlushReq.FlushReqBuilder builder = FlushReq.builder()
                .collectionNames(List.of(collectionName))
                .waitFlushedTimeoutMs(DEFAULT_TIMEOUT_MS);
        appendDatabase(builder);
        milvusClient.flush(builder.build());
    }

    private String resolveCollectionName(int dimension) {
        return ragProperties.getMilvus().getCollectionName() + "_" + dimension;
    }

    private float[] toFloatArray(double[] vector) {
        float[] result = new float[vector.length];
        for (int index = 0; index < vector.length; index++) {
            result[index] = (float) vector[index];
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void appendDatabase(Object builder) {
        String databaseName = ragProperties.getMilvus().getDatabaseName();
        if (databaseName == null || databaseName.isBlank()) {
            return;
        }

        String normalized = databaseName.trim();
        if (builder instanceof HasCollectionReq.HasCollectionReqBuilder hasBuilder) {
            hasBuilder.databaseName(normalized);
        } else if (builder instanceof CreateCollectionReq.CreateCollectionReqBuilder createBuilder) {
            createBuilder.databaseName(normalized);
        } else if (builder instanceof LoadCollectionReq.LoadCollectionReqBuilder loadBuilder) {
            loadBuilder.databaseName(normalized);
        } else if (builder instanceof CreateIndexReq.CreateIndexReqBuilder createIndexBuilder) {
            createIndexBuilder.databaseName(normalized);
        } else if (builder instanceof GetCollectionStatsReq.GetCollectionStatsReqBuilder statsBuilder) {
            statsBuilder.databaseName(normalized);
        } else if (builder instanceof FlushReq.FlushReqBuilder flushBuilder) {
            flushBuilder.databaseName(normalized);
        }
    }

    public record ChunkDocument(String id, String content, String docId, String title, String category) {
    }

    public record SearchHit(int rank, double score, String content, Map<String, String> metadata) {
    }

    public record PreparedCollection(String collectionName, boolean created) {
    }
}
