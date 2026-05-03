package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeDocumentChunkServiceTest {

    @Test
    void chunkFileShouldSplitByStructureAndKeepSectionTitle() {
        TikaDocumentParseService parseService = mock(TikaDocumentParseService.class);
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getChunking().setChunkSize(700);
        properties.getChunking().setChunkOverlap(120);
        KnowledgeDocumentChunkService service = new KnowledgeDocumentChunkService(parseService, properties);

        String content = """
                # Order Policy

                Within 7 days after receipt, unused goods that still allow resale can be returned without reason.

                Return shipping is paid by the customer unless the product has a quality issue.

                # Member Benefits

                Member points can offset cash at checkout. Every 100 points equal 1 unit of currency.
                """;

        when(parseService.parseFile(any())).thenReturn(ParseResult.success(
                "text/markdown",
                content,
                Map.of("resourceName", "policy.md")
        ));

        DocumentChunkResponse response = service.chunkFile(new MockMultipartFile("file", "policy.md", "text/markdown", content.getBytes()));

        assertTrue(response.success());
        assertEquals(2, response.chunkCount());
        assertEquals("Order Policy", response.chunks().get(0).sectionTitle());
        assertTrue(response.chunks().get(0).content().contains("Within 7 days"));
        assertEquals("Member Benefits", response.chunks().get(1).sectionTitle());
    }

    @Test
    void chunkFileShouldReturnFailureWhenParseFails() {
        TikaDocumentParseService parseService = mock(TikaDocumentParseService.class);
        KnowledgeDocumentChunkService service = new KnowledgeDocumentChunkService(parseService, new KnowledgeProperties());

        when(parseService.parseFile(any())).thenReturn(ParseResult.failure("parse failed"));

        DocumentChunkResponse response = service.chunkFile(new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[0]));

        assertFalse(response.success());
        assertEquals("parse failed", response.errorMessage());
    }
}
