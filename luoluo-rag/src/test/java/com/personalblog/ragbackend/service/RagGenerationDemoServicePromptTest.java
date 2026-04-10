package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagGenerationDemoServicePromptTest {

    @Test
    void generateShouldPassContextAndQueryIntoChatService() {
        SiliconFlowEmbeddingDemoService embeddingService = mock(SiliconFlowEmbeddingDemoService.class);
        SiliconFlowChatDemoService chatService = mock(SiliconFlowChatDemoService.class);
        RagGenerationDemoService service = new RagGenerationDemoService(new ObjectMapper(), embeddingService, chatService);

        when(embeddingService.search(any(RagEmbeddingSearchRequest.class))).thenReturn(new RagEmbeddingSearchResponse(
                "七天内还能退货吗",
                "demo-hash-embedding-v1",
                6,
                64,
                "HYBRID",
                1,
                true,
                "demo",
                "heuristic-rerank-v1",
                List.of(new RagEmbeddingSearchResult(
                        1,
                        0.97,
                        "签收后 7 天内，未使用且不影响二次销售的商品支持无理由退货。",
                        Map.of("doc_id", "policy_001", "title", "退货政策", "category", "return_policy")
                ))
        ));
        when(chatService.chatWithTools(any(), any(), any())).thenReturn(new SiliconFlowChatDemoService.ToolChatRoundResponse(
                "chatcmpl-first",
                "Qwen/Qwen3-32B",
                "我已经看过目录，将直接回答。",
                List.of()
        ));
        when(chatService.chat(any(RagDemoChatRequest.class))).thenReturn(new RagDemoChatResponse(
                "chatcmpl-002", "Qwen/Qwen3-32B", "支持 7 天无理由退货。[1]", "stop", 80, 25, 105
        ));

        service.generate(new RagGenerationRequest("七天内还能退货吗", 1, "请严格引用资料回答"));

        ArgumentCaptor<RagDemoChatRequest> captor = ArgumentCaptor.forClass(RagDemoChatRequest.class);
        verify(chatService).chat(captor.capture());
        RagDemoChatRequest chatRequest = captor.getValue();
        assertTrue(chatRequest.systemPrompt().contains("严格引用资料"));
        assertTrue(chatRequest.message().contains("【参考资料】"));
        assertTrue(chatRequest.message().contains("退货政策"));
        assertTrue(chatRequest.message().contains("【用户问题】\n七天内还能退货吗"));
    }
}
