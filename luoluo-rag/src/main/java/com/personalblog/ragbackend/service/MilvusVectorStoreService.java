package com.personalblog.ragbackend.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.personalblog.ragbackend.rag.config.RagProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Milvus 的向量存储服务。
 */
@Service
@ConditionalOnBean(MilvusClientV2.class)
public class MilvusVectorStoreService {
    private static final String ID_FIELD = "chunk_id";
    private static final String CONTENT_FIELD = "content";
    private static final String DOC_ID_FIELD = "doc_id";
    private static final String TITLE_FIELD = "title";
    private static final String VECTOR_FIELD = "vector";

    private final MilvusClientV2 milvusClient;
    private final RagProperties ragProperties;

    public MilvusVectorStoreService(MilvusClientV2 milvusClient, RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.ragProperties = ragProperties;
    }

    public String prepareCollection(int dimension) {
        String collectionName = resolveCollectionName(dimension);
        if (!hasCollection(collectionName)) {
            createCollection(collectionName, dimension);
        }
        loadCollection(collectionName);
        return collectionName;
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

    public List<SearchHit> search(String collectionName, double[] queryVector, int topK) {
        SearchResp response = milvusClient.search(SearchReq.builder()
                .collectionName(collectionName)
                .annsField(VECTOR_FIELD)
                .topK(topK)
                .metricType(IndexParam.MetricType.COSINE)
                .outputFields(List.of(CONTENT_FIELD, DOC_ID_FIELD, TITLE_FIELD))
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
        CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .description("Demo RAG chunks stored in Milvus")
                .dimension(dimension)
                .primaryFieldName(ID_FIELD)
                .idType(DataType.VarChar)
                .maxLength(128)
                .vectorFieldName(VECTOR_FIELD)
                .metricType(IndexParam.MetricType.COSINE.name())
                .autoID(false)
                .enableDynamicField(true);
        appendDatabase(builder);
        milvusClient.createCollection(builder.build());
    }

    private void loadCollection(String collectionName) {
        LoadCollectionReq.LoadCollectionReqBuilder builder = LoadCollectionReq.builder()
                .collectionName(collectionName)
                .sync(true)
                .timeout(60000L);
        appendDatabase(builder);
        milvusClient.loadCollection(builder.build());
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
        }
    }

    public record ChunkDocument(String id, String content, String docId, String title) {
    }

    public record SearchHit(int rank, double score, String content, Map<String, String> metadata) {
    }
}
