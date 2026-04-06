package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.dto.document.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChunkServiceTest {

    @Test
    void chunkFileShouldSplitByStructureAndKeepSectionTitle() {
        TikaParseService tikaParseService = mock(TikaParseService.class);
        DocumentChunkService service = new DocumentChunkService(tikaParseService);

        String content = """
                # Order Policy

                Within 7 days after receipt, unused goods that still allow resale can be returned without reason.

                Return shipping is paid by the customer unless the product has a quality issue.

                # Member Benefits

                Member points can offset cash at checkout. Every 100 points equal 1 unit of currency.
                """;

        when(tikaParseService.parseFile(any())).thenReturn(ParseResult.success(
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
        TikaParseService tikaParseService = mock(TikaParseService.class);
        DocumentChunkService service = new DocumentChunkService(tikaParseService);

        when(tikaParseService.parseFile(any())).thenReturn(ParseResult.failure("parse failed"));

        DocumentChunkResponse response = service.chunkFile(new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[0]));

        assertFalse(response.success());
        assertEquals("parse failed", response.errorMessage());
    }
}
