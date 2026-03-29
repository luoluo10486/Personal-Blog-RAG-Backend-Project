package com.personalblog.ragbackend;

import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void healthEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/luoluo/rag/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiUrl").exists())
                .andExpect(jsonPath("$.data.model").exists());
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
