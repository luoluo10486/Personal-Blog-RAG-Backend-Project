package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannel;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelType;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import com.personalblog.ragbackend.rag.core.retrieve.postprocessor.DeduplicatePostProcessor;
import com.personalblog.ragbackend.rag.core.retrieve.postprocessor.RerankPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalEngineTest {

    @Test
    void shouldForwardRequestToMultiChannelEngine() {
        List<RetrievedChunk> expected = List.of(chunk("c1", "answer-1", 0.9f));
        RecordingMultiChannelRetrievalEngine multiChannelRetrievalEngine = new RecordingMultiChannelRetrievalEngine(expected);
        KnowledgeRetrievalEngine adapter = new KnowledgeRetrievalEngine(multiChannelRetrievalEngine);

        List<RetrievedChunk> result = adapter.retrieve(new RetrieveRequest("kb-001", "what is rag", 3));

        assertThat(result).isEqualTo(expected);
        assertThat(multiChannelRetrievalEngine.lastContext).isNotNull();
        assertThat(multiChannelRetrievalEngine.lastContext.getMetadataString("baseCode")).isEqualTo("kb-001");
        assertThat(multiChannelRetrievalEngine.lastContext.getMetadataString("collectionName")).isEqualTo("kb-001");
        assertThat(multiChannelRetrievalEngine.lastContext.getMainQuestion()).isEqualTo("what is rag");
        assertThat(multiChannelRetrievalEngine.lastContext.getTopK()).isEqualTo(3);
    }

    @Test
    void shouldDeduplicateBeforeRerankInMultiChannelEngine() {
        RecordingRerankService rerankService = new RecordingRerankService(List.of(
                chunk("c2", "answer-2", 0.98f),
                chunk("c1", "answer-1", 0.96f)
        ));

        MultiChannelRetrievalEngine engine = new MultiChannelRetrievalEngine(
                List.of(
                        channel("intent", 1, List.of(
                                chunk("c1", "intent-1", 0.70f),
                                chunk("c2", "intent-2", 0.60f)
                        )),
                        channel("vector", 20, List.of(
                                chunk("c1", "vector-1", 0.70f),
                                chunk("c3", "vector-3", 0.50f)
                        ))
                ),
                List.of(
                        new DeduplicatePostProcessor(),
                        new RerankPostProcessor(fixedProvider(rerankService))
                ),
                directExecutor()
        );

        List<RetrievedChunk> result = engine.retrieveKnowledgeChannels(
                List.of(new com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent("what is rag", List.of())),
                2
        );

        assertThat(rerankService.lastQuery).isEqualTo("what is rag");
        assertThat(rerankService.lastCandidates).extracting(RetrievedChunk::getId)
                .containsExactly("c1", "c2", "c3");
        assertThat(rerankService.lastTopN).isEqualTo(2);
        assertThat(result).extracting(RetrievedChunk::getId).containsExactly("c2", "c1");
    }

    private static SearchChannel channel(String name, int priority, List<RetrievedChunk> chunks) {
        return new SearchChannel() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }

            @Override
            public boolean isEnabled(SearchContext context) {
                return true;
            }

            @Override
            public SearchChannelResult search(SearchContext context) {
                return SearchChannelResult.builder()
                        .channelType("intent".equals(name) ? SearchChannelType.INTENT_DIRECTED : SearchChannelType.VECTOR_GLOBAL)
                        .channelName(name)
                        .chunks(chunks)
                        .build();
            }

            @Override
            public SearchChannelType getType() {
                return "intent".equals(name) ? SearchChannelType.INTENT_DIRECTED : SearchChannelType.VECTOR_GLOBAL;
            }
        };
    }

    private static RetrievedChunk chunk(String id, String text, float score) {
        return new RetrievedChunk(id, text, score);
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }

    private static ObjectProvider<RerankService> fixedProvider(RerankService service) {
        return new ObjectProvider<>() {
            @Override
            public RerankService getObject(Object... args) {
                return service;
            }

            @Override
            public RerankService getIfAvailable() {
                return service;
            }

            @Override
            public RerankService getIfUnique() {
                return service;
            }

            @Override
            public RerankService getObject() {
                return service;
            }
        };
    }

    private static final class RecordingMultiChannelRetrievalEngine extends MultiChannelRetrievalEngine {
        private final List<RetrievedChunk> response;
        private SearchContext lastContext;

        private RecordingMultiChannelRetrievalEngine(List<RetrievedChunk> response) {
            super(List.of(), List.of(), directExecutor());
            this.response = response;
        }

        @Override
        public List<RetrievedChunk> retrieveKnowledgeChannels(SearchContext context) {
            this.lastContext = context;
            return response;
        }
    }

    private static final class RecordingRerankService implements RerankService {
        private final List<RetrievedChunk> response;
        private String lastQuery;
        private List<RetrievedChunk> lastCandidates;
        private int lastTopN;

        private RecordingRerankService(List<RetrievedChunk> response) {
            this.response = response;
        }

        @Override
        public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
            this.lastQuery = query;
            this.lastCandidates = candidates;
            this.lastTopN = topN;
            return response;
        }
    }
}
