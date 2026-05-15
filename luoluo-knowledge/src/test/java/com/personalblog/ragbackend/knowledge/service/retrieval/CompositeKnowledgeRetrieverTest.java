package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeKnowledgeRetrieverTest {

    @Test
    void shouldDelegateDirectlyToMultiChannelEngine() {
        List<RetrievedChunk> expected = List.of(
                new RetrievedChunk("c1", "chunk-1", 0.91f)
        );
        RecordingMultiChannelRetrievalEngine engine = new RecordingMultiChannelRetrievalEngine(expected);
        CompositeKnowledgeRetriever retriever = new CompositeKnowledgeRetriever(engine);

        List<RetrievedChunk> result = retriever.retrieve("kb-002", "question", 4);

        assertThat(result).isEqualTo(expected);
        assertThat(engine.lastContext).isNotNull();
        assertThat(engine.lastContext.getMetadataString("baseCode")).isEqualTo("kb-002");
        assertThat(engine.lastContext.getMetadataString("collectionName")).isEqualTo("kb-002");
        assertThat(engine.lastContext.getMainQuestion()).isEqualTo("question");
        assertThat(engine.lastContext.getTopK()).isEqualTo(4);
    }

    private static final class RecordingMultiChannelRetrievalEngine extends MultiChannelRetrievalEngine {
        private final List<RetrievedChunk> response;
        private SearchContext lastContext;

        private RecordingMultiChannelRetrievalEngine(List<RetrievedChunk> response) {
            super(List.of(), List.of(), Runnable::run);
            this.response = response;
        }

        @Override
        public List<RetrievedChunk> retrieveKnowledgeChannels(SearchContext context) {
            this.lastContext = context;
            return response;
        }
    }
}
