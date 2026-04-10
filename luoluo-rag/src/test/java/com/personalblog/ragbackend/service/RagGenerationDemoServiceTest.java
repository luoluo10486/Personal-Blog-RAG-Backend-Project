package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagGenerationDemoServiceTest {

    @Test
    void generateShouldBuildAnswerAndCitationsFromRetrievedChunks() {
        SiliconFlowEmbeddingDemoService embeddingService = mock(SiliconFlowEmbeddingDemoService.class);
        SiliconFlowChatDemoService chatService = mock(SiliconFlowChatDemoService.class);
        RagGenerationDemoService service = new RagGenerationDemoService(new ObjectMapper(), embeddingService, chatService);

        when(embeddingService.search(any(RagEmbeddingSearchRequest.class))).thenReturn(new RagEmbeddingSearchResponse(
                "订单号 2026012345 的物流状态",
                "demo-hash-embedding-v1",
                6,
                64,
                "HYBRID",
                3,
                true,
                "demo",
                "heuristic-rerank-v1",
                List.of(
                        new RagEmbeddingSearchResult(
                                1,
                                0.998,
                                "订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出，承运商顺丰，当前状态运输中。",
                                Map.of("doc_id", "logistics_002", "title", "物流状态", "category", "logistics")
                        ),
                        new RagEmbeddingSearchResult(
                                2,
                                0.886,
                                "订单发货后 24 小时内会更新物流信息，用户可在订单详情页查看配送进度。",
                                Map.of("doc_id", "logistics_001", "title", "物流说明", "category", "logistics")
                        )
                )
        ));
        when(chatService.chatWithTools(any(), any(), any())).thenReturn(new SiliconFlowChatDemoService.ToolChatRoundResponse(
                "chatcmpl-first",
                "Qwen/Qwen3-32B",
                null,
                List.of(new SiliconFlowChatDemoService.ToolCall(
                        "call_001",
                        "getRetrievedChunkByIndex",
                        "{\"index\":1}",
                        new ObjectMapper().createObjectNode()
                                .put("id", "call_001")
                                .put("type", "function")
                                .set("function", new ObjectMapper().createObjectNode()
                                        .put("name", "getRetrievedChunkByIndex")
                                        .put("arguments", "{\"index\":1}"))
                ))
        ));
        when(chatService.completeToolChat(any(), any(), any(), any())).thenReturn(new RagDemoChatResponse(
                "chatcmpl-001",
                "Qwen/Qwen3-32B",
                "订单已从杭州仓发出，承运商为顺丰，当前状态为运输中。[1] 发货后 24 小时内会更新物流信息，可在订单详情页查看配送进度。[2]",
                "stop",
                128,
                66,
                194
        ));

        RagGenerationResponse response = service.generate(
                new com.personalblog.ragbackend.dto.rag.RagGenerationRequest("订单号 2026012345 的物流状态", 2, null)
        );

        assertEquals("订单号 2026012345 的物流状态", response.query());
        assertEquals("Qwen/Qwen3-32B", response.model());
        assertEquals("HYBRID", response.recallMode());
        assertEquals(2, response.retrievedChunkCount());
        assertEquals(true, response.functionCallApplied());
        assertEquals(List.of("getRetrievedChunkByIndex"), response.calledTools());
        assertEquals(2, response.citations().size());
        assertEquals(1, response.citations().get(0).index());
        assertEquals("物流状态", response.citations().get(0).title());
        assertEquals("logistics_002", response.citations().get(0).docId());
    }
}
