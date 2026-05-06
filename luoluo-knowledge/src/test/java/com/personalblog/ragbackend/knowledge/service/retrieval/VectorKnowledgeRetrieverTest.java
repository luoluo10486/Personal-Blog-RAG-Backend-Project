package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.infra.ai.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeChunkMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceId;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreService;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VectorKnowledgeRetrieverTest {

    @Test
    void shouldMapVectorHitsBackToKnowledgeChunks() {
        KnowledgeProperties properties = createProperties();
        RecordingEmbeddingService embeddingService = new RecordingEmbeddingService(List.of(0.1f, 0.2f, 0.3f));
        RecordingVectorStoreService vectorStoreService = new RecordingVectorStoreService(List.of(
                new VectorSearchHit(
                        "vec-1",
                        0.87,
                        "refund content",
                        Map.of(
                                "chunkId", "chunk-1",
                                "documentId", "doc-1",
                                "baseCode", "refund_center",
                                "title", "Refund Policy",
                                "sourceUrl", "https://example.com/refund",
                                "chunkIndex", 2
                        )
                )
        ));
        VectorKnowledgeRetriever retriever = new VectorKnowledgeRetriever(
                properties,
                new KnowledgeVectorSpaceResolver(properties),
                fixedProvider(embeddingService),
                fixedProvider(vectorStoreService),
                fixedProvider((KnowledgeChunkMapper) null),
                fixedProvider((KnowledgeDocumentMapper) null)
        );

        List<KnowledgeChunk> result = retriever.retrieveCandidates(new RetrieveRequest("Refund Center", "Can I refund?", 4));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(new KnowledgeChunk(
                "chunk-1",
                "refund_center",
                "doc-1",
                "Refund Policy",
                "https://example.com/refund",
                2,
                "refund content",
                0.87
        ));
        assertThat(embeddingService.lastText).isEqualTo("Can I refund?");
        assertThat(vectorStoreService.lastTopK).isEqualTo(4);
        assertThat(vectorStoreService.lastCandidateLimit).isEqualTo(12);
        assertThat(vectorStoreService.lastVectorSpace.collectionName()).isEqualTo("kb_refund_center");
        assertThat(vectorStoreService.lastQueryVector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void shouldUseFallbackMetadataWhenFieldsAreMissing() {
        KnowledgeProperties properties = createProperties();
        RecordingEmbeddingService embeddingService = new RecordingEmbeddingService(List.of(0.4f));
        RecordingVectorStoreService vectorStoreService = new RecordingVectorStoreService(List.of(
                new VectorSearchHit("vec-2", 0.42, "faq content", Map.of())
        ));
        VectorKnowledgeRetriever retriever = new VectorKnowledgeRetriever(
                properties,
                new KnowledgeVectorSpaceResolver(properties),
                fixedProvider(embeddingService),
                fixedProvider(vectorStoreService),
                fixedProvider((KnowledgeChunkMapper) null),
                fixedProvider((KnowledgeDocumentMapper) null)
        );

        List<KnowledgeChunk> result = retriever.retrieveCandidates(new RetrieveRequest("FAQ Center", "faq?", 2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(new KnowledgeChunk(
                "vec-2",
                "faq_center",
                "",
                "",
                "",
                0,
                "faq content",
                0.42
        ));
    }

    @Test
    void shouldReturnEmptyWhenDependenciesAreUnavailableOrQuestionBlank() {
        KnowledgeProperties properties = createProperties();
        VectorKnowledgeRetriever missingEmbeddingRetriever = new VectorKnowledgeRetriever(
                properties,
                new KnowledgeVectorSpaceResolver(properties),
                fixedProvider((EmbeddingService) null),
                fixedProvider(new RecordingVectorStoreService(List.of())),
                fixedProvider((KnowledgeChunkMapper) null),
                fixedProvider((KnowledgeDocumentMapper) null)
        );
        VectorKnowledgeRetriever blankQuestionRetriever = new VectorKnowledgeRetriever(
                properties,
                new KnowledgeVectorSpaceResolver(properties),
                fixedProvider(new RecordingEmbeddingService(List.of(0.1f))),
                fixedProvider(new RecordingVectorStoreService(List.of())),
                fixedProvider((KnowledgeChunkMapper) null),
                fixedProvider((KnowledgeDocumentMapper) null)
        );

        assertThat(missingEmbeddingRetriever.isEnabled(new RetrieveRequest("kb", "hi", 1))).isFalse();
        assertThat(blankQuestionRetriever.isEnabled(new RetrieveRequest("kb", "   ", 1))).isFalse();
        assertThat(blankQuestionRetriever.retrieveCandidates(new RetrieveRequest("kb", "   ", 1))).isEmpty();
    }

    @Test
    void shouldDegradeToEmptyWhenVectorSearchThrows() {
        KnowledgeProperties properties = createProperties();
        VectorKnowledgeRetriever retriever = new VectorKnowledgeRetriever(
                properties,
                new KnowledgeVectorSpaceResolver(properties),
                fixedProvider(new RecordingEmbeddingService(List.of(0.1f))),
                fixedProvider(new ThrowingVectorStoreService()),
                fixedProvider((KnowledgeChunkMapper) null),
                fixedProvider((KnowledgeDocumentMapper) null)
        );

        List<KnowledgeChunk> result = retriever.retrieveCandidates(new RetrieveRequest("kb", "question", 3));

        assertThat(result).isEmpty();
    }

    private KnowledgeProperties createProperties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.getSearch().setTopKMultiplier(3);
        properties.getVector().getMilvus().setCollectionPrefix("kb_");
        properties.getDefaults().setEmbeddingModel("Qwen/Qwen3-Embedding-8B");
        properties.getDefaults().setDimension(1536);
        return properties;
    }

    private static <T> ObjectProvider<T> fixedProvider(T value) {
        return new FixedObjectProvider<>(value);
    }

    private static final class RecordingEmbeddingService implements EmbeddingService {
        private final List<Float> vector;
        private String lastText;

        private RecordingEmbeddingService(List<Float> vector) {
            this.vector = vector;
        }

        @Override
        public List<Float> embed(String text) {
            this.lastText = text;
            return vector;
        }

        @Override
        public List<Float> embed(String text, String modelId) {
            return embed(text);
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts) {
            return texts.stream().map(ignored -> vector).toList();
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts, String modelId) {
            return embedBatch(texts);
        }
    }

    private static class RecordingVectorStoreService implements VectorStoreService {
        private final List<VectorSearchHit> hits;
        private KnowledgeVectorSpace lastVectorSpace;
        private List<Float> lastQueryVector;
        private int lastTopK;
        private int lastCandidateLimit;

        private RecordingVectorStoreService(List<VectorSearchHit> hits) {
            this.hits = hits;
        }

        @Override
        public void upsert(KnowledgeVectorSpace vectorSpace, List<KnowledgeVectorDocument> documents) {
        }

        @Override
        public void deleteByIds(KnowledgeVectorSpace vectorSpace, List<String> vectorIds) {
        }

        @Override
        public List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                            List<Float> queryVector,
                                            int topK,
                                            int candidateLimit) {
            this.lastVectorSpace = vectorSpace;
            this.lastQueryVector = queryVector;
            this.lastTopK = topK;
            this.lastCandidateLimit = candidateLimit;
            return hits;
        }
    }

    private static final class ThrowingVectorStoreService extends RecordingVectorStoreService {
        private ThrowingVectorStoreService() {
            super(List.of());
        }

        @Override
        public List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                            List<Float> queryVector,
                                            int topK,
                                            int candidateLimit) {
            throw new IllegalStateException("vector store unavailable");
        }
    }

    private static final class FixedObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            if (value == null) {
                throw new IllegalStateException("No object available");
            }
            return value;
        }

        @Override
        public Stream<T> stream() {
            return value == null ? Stream.empty() : Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
