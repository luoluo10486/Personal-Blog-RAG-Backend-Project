package com.personalblog.ragbackend.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.personalblog.ragbackend.rag.config.RagProperties;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 Milvus 的向量存储与检索服务（支持 dense/sparse/hybrid 粗检索）。
 */
@Service
@ConditionalOnProperty(prefix = "app.rag.milvus", name = "enabled", havingValue = "true")
public class MilvusVectorStoreService {
    private static final String ID_FIELD = "chunk_id";
    private static final String CONTENT_FIELD = "content";
    private static final String DOC_ID_FIELD = "doc_id";
    private static final String TITLE_FIELD = "title";
    private static final String CATEGORY_FIELD = "category";
    private static final String DENSE_VECTOR_FIELD = "vector";
    private static final String SPARSE_VECTOR_FIELD = "sparse_vector";
    private static final String BM25_FUNCTION_NAME = "content_bm25";
    private static final int CONTENT_MAX_LENGTH = 8192;
    private static final int DOC_ID_MAX_LENGTH = 128;
    private static final int TITLE_MAX_LENGTH = 256;
    private static final int CATEGORY_MAX_LENGTH = 128;
    private static final String DENSE_INDEX_NAME = "idx_vector_hnsw";
    private static final String SPARSE_INDEX_NAME = "idx_sparse_bm25";
    private static final String CATEGORY_INDEX_NAME = "idx_category_trie";
    private static final long DEFAULT_TIMEOUT_MS = 60000L;
    private static final List<String> OUTPUT_FIELDS = List.of(ID_FIELD, CONTENT_FIELD, DOC_ID_FIELD, TITLE_FIELD, CATEGORY_FIELD);

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
        UpsertReq.UpsertReqBuilder builder = UpsertReq.builder()
                .collectionName(collectionName)
                .data(buildRows(documents, vectors));
        appendDatabase(builder);
        milvusClient.upsert(builder.build());
    }

    public void insert(String collectionName, List<ChunkDocument> documents, List<double[]> vectors) {
        InsertReq.InsertReqBuilder builder = InsertReq.builder()
                .collectionName(collectionName)
                .data(buildRows(documents, vectors));
        appendDatabase(builder);
        milvusClient.insert(builder.build());
        flushCollection(collectionName);
    }

    public boolean isCollectionEmpty(String collectionName) {
        GetCollectionStatsReq.GetCollectionStatsReqBuilder builder = GetCollectionStatsReq.builder()
                .collectionName(collectionName);
        appendDatabase(builder);
        Long numOfEntities = milvusClient.getCollectionStats(builder.build()).getNumOfEntities();
        return numOfEntities == null || numOfEntities == 0L;
    }

    public List<SearchHit> denseSearch(String collectionName, double[] queryVector, int topK, int nprobe,
                                       ConsistencyLevel consistencyLevel) {
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(DENSE_VECTOR_FIELD)
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(OUTPUT_FIELDS)
                .data(List.of(new FloatVec(toFloatArray(queryVector))))
                .searchParams(Map.of("nprobe", nprobe))
                .consistencyLevel(consistencyLevel);
        appendDatabase(builder);
        return toSearchHits(milvusClient.search(builder.build()));
    }

    public List<SearchHit> sparseSearch(String collectionName, String queryText, int topK, double dropRatioSearch,
                                        ConsistencyLevel consistencyLevel) {
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(SPARSE_VECTOR_FIELD)
                .topK(topK)
                .metricType(IndexParam.MetricType.BM25)
                .outputFields(OUTPUT_FIELDS)
                .data(List.of(new EmbeddedText(queryText)))
                .searchParams(Map.of("drop_ratio_search", dropRatioSearch))
                .consistencyLevel(consistencyLevel);
        appendDatabase(builder);
        return toSearchHits(milvusClient.search(builder.build()));
    }

    public List<SearchHit> hybridSearch(String collectionName, String queryText, double[] queryVector, int topK,
                                        int denseRecallTopK, int sparseRecallTopK, int nprobe,
                                        double dropRatioSearch, int rrfK, ConsistencyLevel consistencyLevel) {
        AnnSearchReq denseReq = AnnSearchReq.builder()
                .vectorFieldName(DENSE_VECTOR_FIELD)
                .vectors(List.of(new FloatVec(toFloatArray(queryVector))))
                .metricType(IndexParam.MetricType.COSINE)
                .params(String.format(Locale.ROOT, "{\"nprobe\": %d}", nprobe))
                .topK(denseRecallTopK)
                .build();

        AnnSearchReq sparseReq = AnnSearchReq.builder()
                .vectorFieldName(SPARSE_VECTOR_FIELD)
                .vectors(List.of(new EmbeddedText(queryText)))
                .metricType(IndexParam.MetricType.BM25)
                .params(String.format(Locale.ROOT, "{\"drop_ratio_search\": %.4f}", dropRatioSearch))
                .topK(sparseRecallTopK)
                .build();

        HybridSearchReq.HybridSearchReqBuilder builder = HybridSearchReq.builder()
                .collectionName(collectionName)
                .searchRequests(List.of(denseReq, sparseReq))
                .ranker(new RRFRanker(rrfK))
                .topK(topK)
                .outFields(OUTPUT_FIELDS)
                .consistencyLevel(consistencyLevel);
        appendDatabase(builder);
        return toSearchHits(milvusClient.hybridSearch(builder.build()));
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
                        .enableAnalyzer(true)
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
                        .fieldName(DENSE_VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(dimension)
                        .build())
                .addField(AddFieldReq.builder()
                        .fieldName(SPARSE_VECTOR_FIELD)
                        .dataType(DataType.SparseFloatVector)
                        .build())
                .addFunction(CreateCollectionReq.Function.builder()
                        .name(BM25_FUNCTION_NAME)
                        .functionType(FunctionType.BM25)
                        .inputFieldNames(List.of(CONTENT_FIELD))
                        .outputFieldNames(List.of(SPARSE_VECTOR_FIELD))
                        .build());

        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .description("RAG 演示 chunks（dense + sparse + hybrid）")
                .collectionSchema(schema);
        appendDatabase(builder);
        milvusClient.createCollection(builder.build());
    }

    private void createIndexes(String collectionName) {
        IndexParam denseIndex = IndexParam.builder()
                .fieldName(DENSE_VECTOR_FIELD)
                .indexName(DENSE_INDEX_NAME)
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of(
                        "M", 16,
                        "efConstruction", 256
                ))
                .build();

        IndexParam sparseIndex = IndexParam.builder()
                .fieldName(SPARSE_VECTOR_FIELD)
                .indexName(SPARSE_INDEX_NAME)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.BM25)
                .build();

        IndexParam categoryIndex = IndexParam.builder()
                .fieldName(CATEGORY_FIELD)
                .indexName(CATEGORY_INDEX_NAME)
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        CreateIndexReq.CreateIndexReqBuilder builder = CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(List.of(denseIndex, sparseIndex, categoryIndex))
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

    private List<JsonObject> buildRows(List<ChunkDocument> documents, List<double[]> vectors) {
        List<JsonObject> rows = new ArrayList<>(documents.size());
        for (int index = 0; index < documents.size(); index++) {
            ChunkDocument document = documents.get(index);
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, document.id());
            row.addProperty(CONTENT_FIELD, document.content());
            row.addProperty(DOC_ID_FIELD, document.docId());
            row.addProperty(TITLE_FIELD, document.title());
            row.addProperty(CATEGORY_FIELD, document.category());
            row.add(DENSE_VECTOR_FIELD, toJsonArray(vectors.get(index)));
            rows.add(row);
        }
        return rows;
    }

    private JsonArray toJsonArray(double[] vector) {
        JsonArray vectorArray = new JsonArray();
        for (double value : vector) {
            vectorArray.add(value);
        }
        return vectorArray;
    }

    private List<SearchHit> toSearchHits(SearchResp response) {
        List<SearchHit> hits = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return hits;
        }

        int rank = 1;
        for (SearchResp.SearchResult result : searchResults.get(0)) {
            Map<String, Object> entity = result.getEntity() == null ? Map.of() : result.getEntity();
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(ID_FIELD, asString(entity.get(ID_FIELD)));
            metadata.put(DOC_ID_FIELD, asString(entity.get(DOC_ID_FIELD)));
            metadata.put(TITLE_FIELD, asString(entity.get(TITLE_FIELD)));
            metadata.put(CATEGORY_FIELD, asString(entity.get(CATEGORY_FIELD)));
            String id = result.getId() == null ? metadata.get(ID_FIELD) : String.valueOf(result.getId());
            hits.add(new SearchHit(
                    id,
                    rank++,
                    result.getScore(),
                    asString(entity.get(CONTENT_FIELD)),
                    metadata
            ));
        }
        return hits;
    }

    private String resolveCollectionName(int dimension) {
        return ragProperties.getMilvus().getCollectionName() + "_hybrid_" + dimension;
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
        } else if (builder instanceof InsertReq.InsertReqBuilder insertBuilder) {
            insertBuilder.databaseName(normalized);
        } else if (builder instanceof UpsertReq.UpsertReqBuilder upsertBuilder) {
            upsertBuilder.databaseName(normalized);
        } else if (builder instanceof SearchReq.SearchReqBuilder searchBuilder) {
            searchBuilder.databaseName(normalized);
        } else if (builder instanceof HybridSearchReq.HybridSearchReqBuilder hybridBuilder) {
            hybridBuilder.databaseName(normalized);
        }
    }

    public record ChunkDocument(String id, String content, String docId, String title, String category) {
    }

    public record SearchHit(String id, int rank, double score, String content, Map<String, String> metadata) {
    }

    public record PreparedCollection(String collectionName, boolean created) {
    }
}
