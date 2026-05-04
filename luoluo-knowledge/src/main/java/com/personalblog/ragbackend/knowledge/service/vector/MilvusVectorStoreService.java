package com.personalblog.ragbackend.knowledge.service.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBean(MilvusClientV2.class)
@ConditionalOnProperty(prefix = "app.knowledge.vector", name = "type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreService implements VectorStoreService {
    private static final Gson GSON = new Gson();

    private final MilvusClientV2 milvusClient;
    private final KnowledgeProperties knowledgeProperties;
    private final ObjectMapper objectMapper;

    public MilvusVectorStoreService(MilvusClientV2 milvusClient,
                                    KnowledgeProperties knowledgeProperties,
                                    ObjectMapper objectMapper) {
        this.milvusClient = milvusClient;
        this.knowledgeProperties = knowledgeProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(KnowledgeVectorSpace vectorSpace, List<KnowledgeVectorDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        InsertReq.InsertReqBuilder builder = InsertReq.builder()
                .collectionName(vectorSpace.collectionName())
                .data(documents.stream().map(this::toRow).toList());
        applyDatabase(builder);
        milvusClient.insert(builder.build());
    }

    @Override
    public void deleteByIds(KnowledgeVectorSpace vectorSpace, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        String idList = vectorIds.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        DeleteReq.DeleteReqBuilder builder = DeleteReq.builder()
                .collectionName(vectorSpace.collectionName())
                .filter("id in [" + idList + "]");
        applyDatabase(builder);
        milvusClient.delete(builder.build());
    }

    @Override
    public List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                        List<Float> queryVector,
                                        int topK,
                                        int candidateLimit) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(vectorSpace.collectionName())
                .annsField("embedding")
                .topK(Math.max(topK, candidateLimit))
                .metricType(resolveMetricType())
                .outputFields(List.of("id", "content", "metadata"))
                .data(List.of(new io.milvus.v2.service.vector.request.data.FloatVec(toFloatArray(queryVector))))
                .searchParams(Map.of("nprobe", knowledgeProperties.getVector().getMilvus().getNprobe()));
        applyDatabase(builder);
        SearchResp response = milvusClient.search(builder.build());
        List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return List.of();
        }

        List<VectorSearchHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult result : searchResults.get(0)) {
            Map<String, Object> entity = result.getEntity() == null ? Map.of() : result.getEntity();
            hits.add(new VectorSearchHit(
                    result.getId() == null ? asString(entity.get("id")) : String.valueOf(result.getId()),
                    result.getScore(),
                    asString(entity.get("content")),
                    parseMetadata(entity.get("metadata"))
            ));
        }
        return hits;
    }

    private JsonObject toRow(KnowledgeVectorDocument document) {
        JsonObject row = new JsonObject();
        row.addProperty("id", document.vectorId());
        row.addProperty("content", safeContent(document.content()));
        row.add("metadata", GSON.toJsonTree(document.metadata()));
        row.add("embedding", toJsonArray(document.embedding()));
        return row;
    }

    private JsonArray toJsonArray(List<Float> embedding) {
        JsonArray array = new JsonArray();
        for (Float value : embedding) {
            array.add(value);
        }
        return array;
    }

    private float[] toFloatArray(List<Float> embedding) {
        float[] values = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            values[index] = embedding.get(index);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String safeContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= 65535) {
            return content;
        }
        return content.substring(0, 65535);
    }

    private IndexParam.MetricType resolveMetricType() {
        String metricType = knowledgeProperties.getDefaults().getMetricType();
        if (metricType == null || metricType.isBlank()) {
            return IndexParam.MetricType.COSINE;
        }
        return IndexParam.MetricType.valueOf(metricType.trim().toUpperCase());
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void applyDatabase(Object builder) {
        String databaseName = knowledgeProperties.getVector().getMilvus().getDatabaseName();
        if (databaseName == null || databaseName.isBlank()) {
            return;
        }
        String normalized = databaseName.trim();
        if (builder instanceof InsertReq.InsertReqBuilder insertReqBuilder) {
            insertReqBuilder.databaseName(normalized);
        } else if (builder instanceof DeleteReq.DeleteReqBuilder deleteReqBuilder) {
            deleteReqBuilder.databaseName(normalized);
        } else if (builder instanceof SearchReq.SearchReqBuilder searchReqBuilder) {
            searchReqBuilder.databaseName(normalized);
        }
    }
}
