package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategyFactory;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.FixedSizeChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.chunk.strategy.StructureAwareChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.parser.DocumentParserSelector;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.document.TikaDocumentParseService;
import com.personalblog.ragbackend.knowledge.service.ingest.node.ChunkIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.node.FinalizeIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.node.ParseIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.node.PlanIngestionNode;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionEngineTest {

    @Test
    void shouldOnlyBuildPlanInPlanOnlyMode() {
        KnowledgeIngestionEngine engine = createPreviewEngine(createProperties());

        KnowledgeIngestionResult result = engine.execute(
                new KnowledgeIngestionRequest("Order Center", null, KnowledgeIngestionMode.PLAN_ONLY)
        );

        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().baseCode()).isEqualTo("order_center");
        assertThat(result.parseResult()).isNull();
        assertThat(result.chunkResponse()).isNull();
        assertThat(result.ingestionSummary()).isNull();
    }

    @Test
    void shouldParseAndChunkInPreviewMode() {
        KnowledgeIngestionEngine engine = createPreviewEngine(createProperties());

        KnowledgeIngestionResult result = engine.execute(
                new KnowledgeIngestionRequest("Order Center", markdownFile(), KnowledgeIngestionMode.PREVIEW)
        );

        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().baseCode()).isEqualTo("order_center");
        assertThat(result.parseResult()).isNotNull();
        assertThat(result.parseResult().success()).isTrue();
        assertThat(result.chunkResponse()).isNotNull();
        assertThat(result.chunkResponse().success()).isTrue();
        assertThat(result.chunkResponse().chunkCount()).isEqualTo(2);
        assertThat(result.chunkResponse().chunks())
                .extracting(chunk -> chunk.sectionTitle())
                .containsExactly("Order Policy", "Member Benefits");
        assertThat(result.ingestionSummary()).isNull();
    }

    @Test
    void shouldReturnParseAndChunkFailuresWhenPreviewFileIsMissing() {
        KnowledgeIngestionEngine engine = createPreviewEngine(createProperties());

        KnowledgeIngestionResult result = engine.execute(
                new KnowledgeIngestionRequest("default", null, KnowledgeIngestionMode.PREVIEW)
        );

        assertThat(result.plan()).isNotNull();
        assertThat(result.parseResult()).isNotNull();
        assertThat(result.parseResult().success()).isFalse();
        assertThat(result.chunkResponse()).isNotNull();
        assertThat(result.chunkResponse().success()).isFalse();
        assertThat(result.ingestionSummary()).isNull();
    }

    @Test
    void shouldReportFailedIngestionWhenFinalizeRunsWithoutPersistedArtifacts() {
        KnowledgeIngestionEngine engine = createIngestFinalizeEngine(createProperties());

        KnowledgeIngestionResult result = engine.execute(
                new KnowledgeIngestionRequest("default", markdownFile(), KnowledgeIngestionMode.INGEST)
        );

        assertThat(result.plan()).isNotNull();
        assertThat(result.parseResult()).isNotNull();
        assertThat(result.parseResult().success()).isTrue();
        assertThat(result.chunkResponse()).isNotNull();
        assertThat(result.chunkResponse().success()).isTrue();
        assertThat(result.ingestionSummary()).isNotNull();
        assertThat(result.ingestionSummary().success()).isFalse();
        assertThat(result.ingestionSummary().errorMessage()).isEqualTo("Vector indexing did not complete");
    }

    private KnowledgeIngestionEngine createPreviewEngine(KnowledgeProperties properties) {
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

    private KnowledgeIngestionEngine createIngestFinalizeEngine(KnowledgeProperties properties) {
        KnowledgeVectorSpaceResolver vectorSpaceResolver = new KnowledgeVectorSpaceResolver(properties);
        KnowledgeDocumentChunkService chunkService = new KnowledgeDocumentChunkService(
                properties,
                new ChunkingStrategyFactory(List.of(
                        new StructureAwareChunkingStrategy(),
                        new FixedSizeChunkingStrategy()
                ))
        );
        return new KnowledgeIngestionEngine(List.of(
                new FinalizeIngestionNode(),
                new ChunkIngestionNode(chunkService),
                new ParseIngestionNode(new DocumentParserSelector(List.of(new TikaDocumentParseService()))),
                new PlanIngestionNode(properties, vectorSpaceResolver)
        ));
    }

    private KnowledgeProperties createProperties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setDefaultBaseCode("default");
        properties.getChunking().setChunkSize(700);
        properties.getChunking().setChunkOverlap(120);
        return properties;
    }

    private MockMultipartFile markdownFile() {
        return new MockMultipartFile(
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
    }
}
