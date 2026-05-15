package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannel;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import com.personalblog.ragbackend.rag.core.retrieve.postprocessor.SearchResultPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class MultiChannelRetrievalEngine {
    private final List<SearchChannel> searchChannels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final Executor ragRetrievalExecutor;

    public MultiChannelRetrievalEngine(List<SearchChannel> searchChannels,
                                       List<SearchResultPostProcessor> postProcessors,
                                       @Qualifier("ragRetrievalThreadPoolExecutor") Executor ragRetrievalExecutor) {
        this.searchChannels = searchChannels == null ? List.of() : searchChannels.stream()
                .sorted(Comparator.comparingInt(SearchChannel::getPriority))
                .toList();
        this.postProcessors = postProcessors == null ? List.of() : postProcessors.stream()
                .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder))
                .toList();
        this.ragRetrievalExecutor = ragRetrievalExecutor;
    }

    @RagTraceNode(name = "multi-channel-retrieval", type = "RETRIEVE_CHANNEL")
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
        return retrieveKnowledgeChannels(buildSearchContext(subIntents, topK));
    }

    @RagTraceNode(name = "multi-channel-retrieval", type = "RETRIEVE_CHANNEL")
    public List<RetrievedChunk> retrieveKnowledgeChannels(SearchContext context) {
        SearchContext searchContext = normalizeContext(context);
        List<SearchChannelResult> results = executeSearchChannels(searchContext);
        if (CollUtil.isEmpty(results)) {
            return List.of();
        }
        return executePostProcessors(results, searchContext);
    }

    private List<SearchChannelResult> executeSearchChannels(SearchContext context) {
        List<SearchChannel> enabledChannels = searchChannels.stream()
                .filter(channel -> channel.isEnabled(context))
                .toList();
        if (enabledChannels.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
                .map(channel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return channel.search(context);
                    } catch (Exception exception) {
                        return emptyResult(channel);
                    }
                }, ragRetrievalExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<RetrievedChunk> executePostProcessors(List<SearchChannelResult> results, SearchContext context) {
        List<SearchResultPostProcessor> enabledProcessors = postProcessors.stream()
                .filter(processor -> processor.isEnabled(context))
                .toList();

        List<RetrievedChunk> chunks = results.stream()
                .flatMap(result -> result.getChunks().stream())
                .collect(Collectors.toCollection(ArrayList::new));
        if (enabledProcessors.isEmpty()) {
            return chunks;
        }

        for (SearchResultPostProcessor processor : enabledProcessors) {
            try {
                chunks = new ArrayList<>(processor.process(chunks, results, context));
            } catch (Exception ignored) {
            }
        }
        return chunks;
    }

    private SearchChannelResult emptyResult(SearchChannel channel) {
        return SearchChannelResult.builder()
                .channelType(channel.getType())
                .channelName(channel.getName())
                .chunks(List.of())
                .build();
    }

    private SearchContext buildSearchContext(List<SubQuestionIntent> subIntents, int topK) {
        String question = CollUtil.isEmpty(subIntents) ? "" : subIntents.get(0).subQuestion();
        return SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .intents(subIntents)
                .topK(Math.max(topK, 1))
                .build();
    }

    private SearchContext normalizeContext(SearchContext context) {
        if (context == null) {
            return SearchContext.builder()
                    .originalQuestion("")
                    .rewrittenQuestion("")
                    .topK(1)
                    .build();
        }
        if (context.getTopK() <= 0) {
            context.setTopK(1);
        }
        return context;
    }
}
