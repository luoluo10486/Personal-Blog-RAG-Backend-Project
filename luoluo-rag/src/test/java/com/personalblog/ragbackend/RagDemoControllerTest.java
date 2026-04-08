package com.personalblog.ragbackend;

import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import com.personalblog.ragbackend.service.SiliconFlowEmbeddingDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LuoluoRagTestApplication.class)
@AutoConfigureMockMvc
class RagDemoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SiliconFlowChatDemoService siliconFlowChatDemoService;

    @MockBean
    private SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;

    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/luoluo/rag/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiUrl").exists())
                .andExpect(jsonPath("$.data.model").exists())
                .andExpect(jsonPath("$.data.embeddingApiUrl").exists())
                .andExpect(jsonPath("$.data.embeddingModel").exists())
                .andExpect(jsonPath("$.data.embeddingProvider").exists())
                .andExpect(jsonPath("$.data.milvusEnabled").exists())
                .andExpect(jsonPath("$.data.retrievalMode").exists())
                .andExpect(jsonPath("$.data.rerankEnabled").exists())
                .andExpect(jsonPath("$.data.rerankProvider").exists());
    }

    @Test
    void embeddingSearchEndpointShouldReturnDelegatedResponse() throws Exception {
        doReturn(new RagEmbeddingSearchResponse(
                "Can I still return something after a week?",
                "Qwen/Qwen3-Embedding-8B",
                6,
                1024,
                "HYBRID",
                6,
                true,
                "demo",
                "heuristic-rerank-v1",
                java.util.List.of(
                        new RagEmbeddingSearchResult(
                                1,
                                0.9876,
                                "Within 7 days after receipt, unused goods that still allow resale can be returned without reason.",
                                java.util.Map.of("doc_id", "policy_001", "title", "Return Policy")
                        )
                )
        )).when(siliconFlowEmbeddingDemoService).search(any());

        mockMvc.perform(post("/luoluo/rag/demo/embedding/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Can I still return something after a week?",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("Can I still return something after a week?"))
                .andExpect(jsonPath("$.data.embeddingModel").value("Qwen/Qwen3-Embedding-8B"))
                .andExpect(jsonPath("$.data.recallMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.results[0].metadata.title").value("Return Policy"));
    }

    @Test
    void streamEndpointShouldExposeSse() throws Exception {
        when(siliconFlowChatDemoService.streamChat(any(RagDemoChatRequest.class))).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter();
            emitter.complete();
            return emitter;
        });

        var result = mockMvc.perform(post("/luoluo/rag/demo/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
