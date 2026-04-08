package com.personalblog.ragbackend;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LuoluoRagTestApplication.class, properties = {
        "app.rag.enabled=true",
        "app.rag.embedding-provider=demo",
        "app.rag.demo-embedding-dimension=64",
        "app.rag.milvus.enabled=true",
        "app.rag.milvus.uri=http://127.0.0.1:19530",
        "app.rag.milvus.token=",
        "app.rag.milvus.collection-name=rag_demo_chunks_it"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "MILVUS_IT_ENABLED", matches = "true")
class MilvusEmbeddingSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void embeddingSearchShouldUseMilvusWhenContainerIsAvailable() throws Exception {
        Assumptions.assumeTrue(isMilvusReachable("127.0.0.1", 19530), "Milvus is not reachable on 127.0.0.1:19530");

        mockMvc.perform(post("/luoluo/rag/demo/embedding/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Can I still return something after a week?",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embeddingModel").value("demo-hash-embedding-v1"))
                .andExpect(jsonPath("$.data.chunkCount").value(6))
                .andExpect(jsonPath("$.data.vectorDimension").value(64))
                .andExpect(jsonPath("$.data.recallMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.results[0].metadata.doc_id").exists())
                .andExpect(jsonPath("$.data.results[0].metadata.title").exists());
    }

    private boolean isMilvusReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
