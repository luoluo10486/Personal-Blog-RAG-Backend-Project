package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiliconFlowEmbeddingDemoServiceTest {

    @Test
    void parseEmbeddingResponseShouldReturnVectors() throws Exception {
        SiliconFlowEmbeddingDemoService service = new SiliconFlowEmbeddingDemoService(
                mock(HttpClient.class),
                new ObjectMapper(),
                buildRagProperties(),
                new DemoHashEmbeddingService(buildRagProperties()),
                java.util.Optional.empty()
        );

        List<double[]> vectors = service.parseEmbeddingResponse("""
                {
                  "data": [
                    {"embedding": [0.1, 0.2, 0.3]},
                    {"embedding": [0.4, 0.5, 0.6]}
                  ]
                }
                """);

        assertEquals(2, vectors.size());
        assertEquals(3, vectors.get(0).length);
        assertEquals(0.5, vectors.get(1)[1]);
    }

    @Test
    void searchShouldSortChunksByCosineSimilarity() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> chunkResponse = mock(HttpResponse.class);
        HttpResponse<String> queryResponse = mock(HttpResponse.class);

        when(chunkResponse.statusCode()).thenReturn(200);
        when(chunkResponse.body()).thenReturn("""
                {
                  "data": [
                    {"embedding": [1.0, 0.0]},
                    {"embedding": [0.8, 0.6]},
                    {"embedding": [0.2, 0.98]},
                    {"embedding": [-1.0, 0.0]},
                    {"embedding": [0.6, 0.8]}
                  ]
                }
                """);
        when(queryResponse.statusCode()).thenReturn(200);
        when(queryResponse.body()).thenReturn("""
                {
                  "data": [
                    {"embedding": [1.0, 0.0]}
                  ]
                }
                """);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(chunkResponse, queryResponse);

        SiliconFlowEmbeddingDemoService service = new SiliconFlowEmbeddingDemoService(
                httpClient,
                new ObjectMapper(),
                buildRagProperties(),
                new DemoHashEmbeddingService(buildRagProperties()),
                java.util.Optional.empty()
        );

        RagEmbeddingSearchResponse response = service.search(new RagEmbeddingSearchRequest("七天内还能退货吗", 3));

        assertEquals("Qwen/Qwen3-Embedding-8B", response.embeddingModel());
        assertEquals(5, response.chunkCount());
        assertEquals(2, response.vectorDimension());
        assertEquals(3, response.results().size());
        assertEquals("policy_001", response.results().get(0).metadata().get("doc_id"));
        assertEquals("退货政策", response.results().get(0).metadata().get("title"));
        assertEquals("return_policy", response.results().get(0).metadata().get("category"));
        assertEquals("policy_001", response.results().get(1).metadata().get("doc_id"));
        assertEquals("policy_002", response.results().get(2).metadata().get("doc_id"));
    }

    private RagProperties buildRagProperties() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setEmbeddingProvider("siliconflow");
        properties.setApiKey("test-key");
        properties.setConnectTimeoutSeconds(30);
        properties.setReadTimeoutSeconds(60);
        properties.setEmbeddingModel("Qwen/Qwen3-Embedding-8B");
        properties.setEmbeddingApiUrl("https://api.siliconflow.cn/v1/embeddings");
        return properties;
    }
}
