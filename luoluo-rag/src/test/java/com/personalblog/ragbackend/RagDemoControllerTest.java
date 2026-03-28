package com.personalblog.ragbackend;

import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
}
