package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

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

    private RagProperties buildRagProperties() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setConnectTimeoutSeconds(30);
        properties.setReadTimeoutSeconds(60);
        return properties;
    }
}
