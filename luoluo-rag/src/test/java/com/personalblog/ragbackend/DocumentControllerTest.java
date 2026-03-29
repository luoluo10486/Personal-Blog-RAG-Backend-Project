package com.personalblog.ragbackend;

import com.personalblog.ragbackend.dto.document.ParseResult;
import com.personalblog.ragbackend.service.TikaParseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LuoluoRagTestApplication.class)
@AutoConfigureMockMvc
class DocumentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TikaParseService tikaParseService;

    @Test
    void parseEndpointShouldReturnParsedResult() throws Exception {
        when(tikaParseService.parseFile(any())).thenReturn(ParseResult.success(
                "text/plain",
                "Hello World",
                Map.of(
                        "resourceName", "test.txt",
                        "X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"
                )
        ));

        mockMvc.perform(multipart("/luoluo/rag/document/parse")
                        .file(new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mimeType").value("text/plain"))
                .andExpect(jsonPath("$.content").value("Hello World"))
                .andExpect(jsonPath("$.contentLength").value(11))
                .andExpect(jsonPath("$.metadata.resourceName").value("test.txt"))
                .andExpect(jsonPath("$.errorMessage").value(nullValue()));
    }
}
