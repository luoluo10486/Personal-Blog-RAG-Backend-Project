package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategyFactory;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.FixedSizeChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.StructureAwareChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.document.TikaDocumentParseService;
import com.personalblog.ragbackend.knowledge.service.ingest.node.ChunkIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.node.ParseIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.node.PlanIngestionNode;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIngestionEngineTest {

    @Test
    void shouldExecutePlanParseAndChunkPipeline() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setDefaultBaseCode("default");
        properties.getChunking().setChunkSize(700);
        properties.getChunking().setChunkOverlap(120);

        KnowledgeIngestionEngine engine = createEngine(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.md",
                "text/markdown",
                """
                # Order Policy

                Within 7 days after receipt, unused goods can be returned.

                # Member Benefits

                Member points can offset cash at checkout.
                """.getBytes()
        );

        KnowledgeIngestionResult result = engine.execute(new KnowledgeIngestionRequest("Order Center", file));

        assertNotNull(result.plan());
        assertEquals("order_center", result.plan().baseCode());
        assertTrue(result.parseResult().success());
        assertTrue(result.chunkResponse().success());
        assertEquals(2, result.chunkResponse().chunkCount());
        assertEquals("Order Policy", result.chunkResponse().chunks().get(0).sectionTitle());
    }

    @Test
    void shouldReturnFailedParseAndChunkWhenFileIsMissing() {
        KnowledgeProperties properties = new KnowledgeProperties();
        KnowledgeIngestionEngine engine = createEngine(properties);

        KnowledgeIngestionResult result = engine.execute(new KnowledgeIngestionRequest("default", null));

        assertNotNull(result.plan());
        assertFalse(result.parseResult().success());
        assertEquals("文件为空", result.parseResult().errorMessage());
        assertFalse(result.chunkResponse().success());
        assertEquals("文件为空", result.chunkResponse().errorMessage());
    }

    private KnowledgeIngestionEngine createEngine(KnowledgeProperties properties) {
        KnowledgeVectorSpaceResolver vectorSpaceResolver = new KnowledgeVectorSpaceResolver(properties);
        KnowledgeDocumentChunkService chunkService = new KnowledgeDocumentChunkService(
                properties,
                new ChunkingStrategyFactory(List.of(
                        new StructureAwareChunkingStrategy(),
                        new FixedSizeChunkingStrategy()
                ))
        );
        return new KnowledgeIngestionEngine(List.of(
                new ChunkIngestionNode(chunkService),
                new ParseIngestionNode(new DocumentParserSelector(List.of(new TikaDocumentParseService()))),
                new PlanIngestionNode(properties, vectorSpaceResolver)
        ));
    }
}
