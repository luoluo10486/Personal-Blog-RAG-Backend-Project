package com.personalblog.ragbackend.client;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.model.RetrievedChunk;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * RagApiClient 客户端类，用于调用外部服务。
 */
@Component
public class RagApiClient {
    private final RestClient restClient;
    private final AppProperties appProperties;

    public RagApiClient(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
    }

    public List<RetrievedChunk> retrieve(String question) {
        var rag = appProperties.getRag();
        if (!rag.isEnabled() || !StringUtils.hasText(rag.getRetrievalUrl())) {
            return List.of();
        }

        try {
            RetrieveResponse response = restClient.post()
                    .uri(rag.getRetrievalUrl())
                    .headers(headers -> withAuth(headers, rag.getApiKey()))
                    .body(Map.of(
                            "query", question,
                            "topK", rag.getTopK()
                    ))
                    .retrieve()
                    .body(RetrieveResponse.class);

            if (response == null || response.chunks() == null) {
                return List.of();
            }

            return response.chunks().stream()
                    .map(item -> new RetrievedChunk(
                            item.id(),
                            item.title(),
                            item.content(),
                            item.score() == null ? 0.0 : item.score()
                    ))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String generate(String question, List<RetrievedChunk> contexts) {
        var rag = appProperties.getRag();
        if (!rag.isEnabled() || !StringUtils.hasText(rag.getLlmUrl())) {
            return "";
        }

        try {
            GenerateResponse response = restClient.post()
                    .uri(rag.getLlmUrl())
                    .headers(headers -> withAuth(headers, rag.getApiKey()))
                    .body(Map.of(
                            "question", question,
                            "contexts", contexts
                    ))
                    .retrieve()
                    .body(GenerateResponse.class);
            return response == null || response.answer() == null ? "" : response.answer();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void withAuth(HttpHeaders headers, String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
    }

    private record RetrieveResponse(List<RetrievedChunkPayload> chunks) {
    }

    private record RetrievedChunkPayload(String id, String title, String content, Double score) {
    }

    private record GenerateResponse(String answer) {
    }
}

