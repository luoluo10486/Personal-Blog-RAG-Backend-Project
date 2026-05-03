package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategyFactory;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.FixedSizeChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.StructureAwareChunkingStrategy;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentChunkServiceTest {

    @Test
    void chunkFileShouldSplitByStructureAndKeepSectionTitle() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getChunking().setChunkSize(700);
        properties.getChunking().setChunkOverlap(120);
        KnowledgeDocumentChunkService service = new KnowledgeDocumentChunkService(
                properties,
                new ChunkingStrategyFactory(java.util.List.of(
                        new StructureAwareChunkingStrategy(),
                        new FixedSizeChunkingStrategy()
                ))
        );

        String content = """
                # Order Policy

                Within 7 days after receipt, unused goods that still allow resale can be returned without reason.

                Return shipping is paid by the customer unless the product has a quality issue.

                # Member Benefits

                Member points can offset cash at checkout. Every 100 points equal 1 unit of currency.
                """;

        DocumentChunkResponse response = service.chunkParsedResult(ParseResult.success(
                "text/markdown",
                content,
                Map.of("resourceName", "policy.md")
        ));

        assertTrue(response.success());
        assertEquals(2, response.chunkCount());
        assertEquals("Order Policy", response.chunks().get(0).sectionTitle());
        assertTrue(response.chunks().get(0).content().contains("Within 7 days"));
        assertEquals("Member Benefits", response.chunks().get(1).sectionTitle());
    }

    @Test
    void chunkFileShouldReturnFailureWhenParseFails() {
        KnowledgeDocumentChunkService service = new KnowledgeDocumentChunkService(
                new KnowledgeProperties(),
                new ChunkingStrategyFactory(java.util.List.of(
                        new StructureAwareChunkingStrategy(),
                        new FixedSizeChunkingStrategy()
                ))
        );

        DocumentChunkResponse response = service.chunkParsedResult(ParseResult.failure("parse failed"));

        assertFalse(response.success());
        assertEquals("parse failed", response.errorMessage());
    }
}
