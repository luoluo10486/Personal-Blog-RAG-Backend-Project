package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeKnowledgeRetrieverTest {

    @Test
    void shouldDelegateToRetrievalEngine() {
        List<KnowledgeChunk> expected = List.of(
                new KnowledgeChunk("c1", "kb", "doc-1", "title-1", "", 0, "content-1", 0.91)
        );
        RecordingKnowledgeRetrievalEngine engine = new RecordingKnowledgeRetrievalEngine(expected);
        CompositeKnowledgeRetriever retriever = new CompositeKnowledgeRetriever(engine);

        List<KnowledgeChunk> result = retriever.retrieve("kb", "question", 3);

        assertThat(result).isEqualTo(expected);
        assertThat(engine.lastRequest).isEqualTo(new RetrieveRequest("kb", "question", 3));
    }

    private static final class RecordingKnowledgeRetrievalEngine extends KnowledgeRetrievalEngine {
        private final List<KnowledgeChunk> response;
        private RetrieveRequest lastRequest;

        private RecordingKnowledgeRetrievalEngine(List<KnowledgeChunk> response) {
            super(List.of(), List.of());
            this.response = response;
        }

        @Override
        public List<KnowledgeChunk> retrieve(RetrieveRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
