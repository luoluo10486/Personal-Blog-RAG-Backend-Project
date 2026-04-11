package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagGenerationIntentRoutingTest {

    @Test
    void generateShouldBypassRetrievalForChitchatIntent() {
        SiliconFlowEmbeddingDemoService embeddingService = mock(SiliconFlowEmbeddingDemoService.class);
        SiliconFlowChatDemoService chatService = mock(SiliconFlowChatDemoService.class);
        IntentClassifierService intentClassifierService = mock(IntentClassifierService.class);
        RagGenerationDemoService service = new RagGenerationDemoService(
                new ObjectMapper(),
                embeddingService,
                chatService,
                intentClassifierService
        );

        when(intentClassifierService.classify(List.of(), "你好")).thenReturn(
                new IntentClassifierService.IntentResult("chitchat", 0.99, "rule")
        );
        when(chatService.chat(any(RagDemoChatRequest.class))).thenReturn(new RagDemoChatResponse(
                "chatcmpl-chitchat",
                "Qwen/Qwen3-32B",
                "你好呀，很高兴为你服务。",
                "stop",
                20,
                12,
                32
        ));

        RagGenerationResponse response = service.generate(new RagGenerationRequest("你好", 3, null));

        assertEquals("你好呀，很高兴为你服务。", response.answer());
        assertEquals("SKIPPED", response.recallMode());
        assertEquals(0, response.retrievedChunkCount());
        assertTrue(response.citations().isEmpty());
        verify(embeddingService, never()).search(any());
    }

    @Test
    void generateShouldReturnClarificationWithoutRetrievalWhenIntentIsClarification() {
        SiliconFlowEmbeddingDemoService embeddingService = mock(SiliconFlowEmbeddingDemoService.class);
        SiliconFlowChatDemoService chatService = mock(SiliconFlowChatDemoService.class);
        IntentClassifierService intentClassifierService = mock(IntentClassifierService.class);
        RagGenerationDemoService service = new RagGenerationDemoService(
                new ObjectMapper(),
                embeddingService,
                chatService,
                intentClassifierService
        );

        when(intentClassifierService.classify(List.of(), "有什么推荐的")).thenReturn(
                new IntentClassifierService.IntentResult("clarification", 0.95, "rule")
        );

        RagGenerationResponse response = service.generate(new RagGenerationRequest("有什么推荐的", 3, null));

        assertEquals("intent-router", response.model());
        assertEquals("SKIPPED", response.recallMode());
        assertTrue(response.answer().contains("请补充更明确的信息"));
        verify(embeddingService, never()).search(any());
        verify(chatService, never()).chat(any());
    }
}
