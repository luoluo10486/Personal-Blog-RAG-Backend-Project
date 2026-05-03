package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.rerank.RerankService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor.ConfidenceThresholdPostProcessor;
import com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor.DeduplicatePostProcessor;
import com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor.RerankPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalEngineTest {

    @Test
    void shouldFilterByConfidenceThresholdBeforeReturningTopK() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getSearch().setConfidenceThreshold(0.6);
        properties.getSearch().getRerank().setEnabled(false);

        KnowledgeCandidateRetriever retriever = new StubCandidateRetriever(List.of(
                chunk("c1", 0.95),
                chunk("c2", 0.72),
                chunk("c3", 0.45)
        ));

        KnowledgeRetrievalEngine engine = new KnowledgeRetrievalEngine(
                List.of(retriever),
                List.of(
                        new DeduplicatePostProcessor(),
                        new ConfidenceThresholdPostProcessor(properties),
                        new RerankPostProcessor(properties, emptyProvider())
                )
        );

        List<KnowledgeChunk> results = engine.retrieve(new RetrieveRequest("default", "score", 5));

        assertThat(results).extracting(KnowledgeChunk::id).containsExactly("c1", "c2");
    }

    @Test
    void shouldFallbackToTopKWhenThresholdFiltersEverythingOut() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getSearch().setConfidenceThreshold(0.99);
        properties.getSearch().getRerank().setEnabled(false);

        KnowledgeCandidateRetriever retriever = new StubCandidateRetriever(List.of(
                chunk("c1", 0.65),
                chunk("c2", 0.52),
                chunk("c3", 0.41)
        ));

        KnowledgeRetrievalEngine engine = new KnowledgeRetrievalEngine(
                List.of(retriever),
                List.of(
                        new DeduplicatePostProcessor(),
                        new ConfidenceThresholdPostProcessor(properties),
                        new RerankPostProcessor(properties, emptyProvider())
                )
        );

        List<KnowledgeChunk> results = engine.retrieve(new RetrieveRequest("default", "logistics", 2));

        assertThat(results).extracting(KnowledgeChunk::id).containsExactly("c1", "c2");
    }

    @Test
    void shouldApplyRerankAsPostProcessorWhenEnabled() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getSearch().setConfidenceThreshold(0.1);
        properties.getSearch().getRerank().setEnabled(true);

        KnowledgeCandidateRetriever retriever = new StubCandidateRetriever(List.of(
                chunk("c1", 0.92),
                chunk("c2", 0.88)
        ));

        RerankService rerankService = (query, candidates, topN) -> List.of(
                new RetrievedChunk("c2", "second", 0.99f),
                new RetrievedChunk("c1", "first", 0.66f)
        );

        KnowledgeRetrievalEngine engine = new KnowledgeRetrievalEngine(
                List.of(retriever),
                List.of(
                        new DeduplicatePostProcessor(),
                        new ConfidenceThresholdPostProcessor(properties),
                        new RerankPostProcessor(properties, fixedProvider(rerankService))
                )
        );

        List<KnowledgeChunk> results = engine.retrieve(new RetrieveRequest("default", "refund policy", 2));

        assertThat(results).extracting(KnowledgeChunk::id).containsExactly("c2", "c1");
        assertThat(results.get(0).score()).isCloseTo(0.99d, org.assertj.core.data.Offset.offset(1e-6d));
    }

    @Test
    void shouldPreferRealRetrieverBeforeNoopAndMergeResultsByScore() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getSearch().setConfidenceThreshold(0.0);
        properties.getSearch().getRerank().setEnabled(false);

        KnowledgeCandidateRetriever noop = new StubCandidateRetriever("noop", 1000, List.of());
        KnowledgeCandidateRetriever jdbc = new StubCandidateRetriever("jdbc", 10, List.of(
                chunk("c1", 0.42),
                chunk("c2", 0.81)
        ));

        KnowledgeRetrievalEngine engine = new KnowledgeRetrievalEngine(
                List.of(noop, jdbc),
                List.of(
                        new DeduplicatePostProcessor(),
                        new ConfidenceThresholdPostProcessor(properties),
                        new RerankPostProcessor(properties, emptyProvider())
                )
        );

        List<KnowledgeChunk> results = engine.retrieve(new RetrieveRequest("default", "question", 2));

        assertThat(results).extracting(KnowledgeChunk::id).containsExactly("c2", "c1");
    }

    @Test
    void shouldKeepHighestScoreWhenMultipleRetrieversReturnSameChunk() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getSearch().setConfidenceThreshold(0.0);
        properties.getSearch().getRerank().setEnabled(false);

        KnowledgeCandidateRetriever first = new StubCandidateRetriever("keyword", 10, List.of(
                chunk("c1", 0.55),
                chunk("c2", 0.61)
        ));
        KnowledgeCandidateRetriever second = new StubCandidateRetriever("vector", 20, List.of(
                chunk("c1", 0.89),
                chunk("c3", 0.74)
        ));

        KnowledgeRetrievalEngine engine = new KnowledgeRetrievalEngine(
                List.of(second, first),
                List.of(
                        new DeduplicatePostProcessor(),
                        new ConfidenceThresholdPostProcessor(properties),
                        new RerankPostProcessor(properties, emptyProvider())
                )
        );

        List<KnowledgeChunk> results = engine.retrieve(new RetrieveRequest("default", "question", 3));

        assertThat(results).extracting(KnowledgeChunk::id).containsExactly("c1", "c3", "c2");
        assertThat(results.get(0).score()).isEqualTo(0.89d);
    }

    private static KnowledgeChunk chunk(String id, double score) {
        return new KnowledgeChunk(
                id,
                "default",
                "doc-" + id,
                "title-" + id,
                "https://example.com/" + id,
                1,
                "content-" + id,
                score
        );
    }

    private static ObjectProvider<RerankService> emptyProvider() {
        return new FixedObjectProvider(null);
    }

    private static ObjectProvider<RerankService> fixedProvider(RerankService service) {
        return new FixedObjectProvider(service);
    }

    private record StubCandidateRetriever(String name, int order, List<KnowledgeChunk> candidates)
            implements KnowledgeCandidateRetriever {

        private StubCandidateRetriever(List<KnowledgeChunk> candidates) {
            this("stub", 100, candidates);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public List<KnowledgeChunk> retrieveCandidates(RetrieveRequest request) {
            return candidates;
        }
    }

    private static final class FixedObjectProvider implements ObjectProvider<RerankService> {
        private final RerankService value;

        private FixedObjectProvider(RerankService value) {
            this.value = value;
        }

        @Override
        public RerankService getObject(Object... args) {
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }

        @Override
        public RerankService getIfAvailable() {
            return value;
        }

        @Override
        public RerankService getIfUnique() {
            return value;
        }

        @Override
        public RerankService getObject() {
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }
    }
}
