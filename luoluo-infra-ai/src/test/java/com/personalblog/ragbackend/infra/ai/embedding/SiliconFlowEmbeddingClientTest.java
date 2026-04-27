package com.personalblog.ragbackend.infra.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiliconFlowEmbeddingClientTest {

    @Test
    void shouldSplitLargeBatchRequests() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response1 = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response2 = mock(HttpResponse.class);

        when(response1.statusCode()).thenReturn(200);
        when(response1.body()).thenReturn(buildOpenAiEmbeddingBody(32, 0));
        when(response2.statusCode()).thenReturn(200);
        when(response2.body()).thenReturn(buildOpenAiEmbeddingBody(1, 32));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response1, response2);

        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(
                httpClient,
                new ObjectMapper(),
                aiProperties()
        );

        List<String> texts = IntStream.range(0, 33)
                .mapToObj(index -> "text-" + index)
                .toList();

        List<List<Float>> embeddings = client.embedBatch(texts, modelTarget());

        assertThat(embeddings).hasSize(33);
        assertThat(embeddings.get(0)).containsExactly(0.0F, 0.5F);
        assertThat(embeddings.get(32)).containsExactly(32.0F, 32.5F);
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private AIModelProperties aiProperties() {
        AIModelProperties properties = new AIModelProperties();
        properties.setReadTimeoutSeconds(5);
        return properties;
    }

    private ModelTarget modelTarget() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setType("openai-compatible");
        provider.setBaseUrl("https://example.test");
        provider.setApiKey("sk-test");

        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("embedding-default");
        candidate.setProvider("siliconflow");
        candidate.setModel("test-embedding-model");
        candidate.setDimension(2);

        return new ModelTarget("embedding-default", candidate, provider);
    }

    private String buildOpenAiEmbeddingBody(int size, int start) {
        StringBuilder builder = new StringBuilder("{\"data\":[");
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                builder.append(',');
            }
            int value = start + index;
            builder.append("{\"embedding\":[")
                    .append(value)
                    .append(',')
                    .append(value)
                    .append(".5]}");
        }
        builder.append("]}");
        return builder.toString();
    }
}
