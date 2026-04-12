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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("SiliconFlow 请求超时（60 秒）", exception.getReason());
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
        assertEquals("SiliconFlow 连接超时（30 秒）", exception.getReason());
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

    @Test
    void parseStreamingResponseShouldIgnoreMalformedChunksAndExposeUsageCallback() throws Exception {
        SiliconFlowChatDemoService service = new SiliconFlowChatDemoService(
                mock(HttpClient.class),
                new ObjectMapper(),
                buildRagProperties()
        );
        List<String> deltas = new ArrayList<>();
        AtomicReference<SiliconFlowChatDemoService.StreamingUsage> usageRef = new AtomicReference<>();
        String streamBody = """
                : keep-alive

                data: {"id":"chatcmpl-456","model":"Qwen/Qwen3-32B","choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}

                event: message
                data: {bad json

                data: {"choices":[{"delta":{"content":[" there",{"text":"!"}]},"finish_reason":"stop"}],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12}}

                data: [DONE]

                """;

        RagDemoChatResponse response = service.parseStreamingResponse(
                new ByteArrayInputStream(streamBody.getBytes(StandardCharsets.UTF_8)),
                new SiliconFlowChatDemoService.StreamEventListener() {
                    @Override
                    public void onDelta(String content) {
                        deltas.add(content);
                    }

                    @Override
                    public void onUsage(SiliconFlowChatDemoService.StreamingUsage usage) {
                        usageRef.set(usage);
                    }
                }
        );

        assertEquals(List.of("Hi", " there!"), deltas);
        assertEquals("chatcmpl-456", response.requestId());
        assertEquals("Hi there!", response.answer());
        assertEquals("stop", response.finishReason());
        assertEquals(12, response.totalTokens());
        assertEquals(9, usageRef.get().promptTokens());
        assertEquals(3, usageRef.get().completionTokens());
        assertEquals(12, usageRef.get().totalTokens());
    }

    @Test
    void chatWithToolsShouldParseReturnedToolCalls() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> toolResponse = mock(HttpResponse.class);
        when(toolResponse.statusCode()).thenReturn(200);
        when(toolResponse.body()).thenReturn("""
                {
                  "id": "chatcmpl-tool-1",
                  "model": "Qwen/Qwen3-32B",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_001",
                            "type": "function",
                            "function": {
                              "name": "getRetrievedChunkByIndex",
                              "arguments": "{\\\"index\\\":1}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(toolResponse);

        SiliconFlowChatDemoService service = new SiliconFlowChatDemoService(
                httpClient,
                new ObjectMapper(),
                buildRagProperties()
        );

        var tools = new ObjectMapper().createArrayNode();
        SiliconFlowChatDemoService.ToolChatRoundResponse response = service.chatWithTools(
                "请先按需调用工具",
                "用户问题",
                tools
        );

        assertEquals("chatcmpl-tool-1", response.requestId());
        assertEquals(1, response.toolCalls().size());
        assertEquals("getRetrievedChunkByIndex", response.toolCalls().get(0).name());
        assertFalse(response.toolCalls().get(0).arguments().isBlank());
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
