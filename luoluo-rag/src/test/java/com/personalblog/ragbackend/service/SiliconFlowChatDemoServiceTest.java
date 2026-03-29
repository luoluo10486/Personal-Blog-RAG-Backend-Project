package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiliconFlowChatDemoServiceTest {

    @Test
    void chatShouldReturnGatewayTimeoutWhenRequestTimesOut() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        SiliconFlowChatDemoService service = new SiliconFlowChatDemoService(
                httpClient,
                new ObjectMapper(),
                buildRagProperties()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.chat(new RagDemoChatRequest(null, "hello"))
        );

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatusCode());
        assertEquals("siliconflow request timed out after 60 seconds", exception.getReason());
    }

    @Test
    void chatShouldReturnGatewayTimeoutWhenConnectTimesOut() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpConnectTimeoutException("HTTP connect timed out"));

        SiliconFlowChatDemoService service = new SiliconFlowChatDemoService(
                httpClient,
                new ObjectMapper(),
                buildRagProperties()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.chat(new RagDemoChatRequest(null, "hello"))
        );

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatusCode());
        assertEquals("siliconflow connect timed out after 30 seconds", exception.getReason());
    }

    @Test
    void parseStreamingResponseShouldCollectDeltaContentAndMetadata() throws Exception {
        SiliconFlowChatDemoService service = new SiliconFlowChatDemoService(
                mock(HttpClient.class),
                new ObjectMapper(),
                buildRagProperties()
        );
        List<String> deltas = new ArrayList<>();
        String streamBody = """
                data: {"id":"chatcmpl-123","model":"Qwen/Qwen3-32B","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":" world"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}

                data: [DONE]

                """;

        RagDemoChatResponse response = service.parseStreamingResponse(
                new ByteArrayInputStream(streamBody.getBytes(StandardCharsets.UTF_8)),
                deltas::add
        );

        assertEquals(List.of("Hello", " world"), deltas);
        assertEquals("chatcmpl-123", response.requestId());
        assertEquals("Qwen/Qwen3-32B", response.model());
        assertEquals("Hello world", response.answer());
        assertEquals("stop", response.finishReason());
        assertEquals(12, response.promptTokens());
        assertEquals(8, response.completionTokens());
        assertEquals(20, response.totalTokens());
    }

    private RagProperties buildRagProperties() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setConnectTimeoutSeconds(30);
        properties.setReadTimeoutSeconds(60);
        return properties;
    }
}
