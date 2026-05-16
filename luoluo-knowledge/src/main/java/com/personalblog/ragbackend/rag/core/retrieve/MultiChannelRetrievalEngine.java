package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannel;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import com.personalblog.ragbackend.rag.core.retrieve.postprocessor.SearchResultPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MultiChannelRetrievalEngine {

    private final List<SearchChannel> searchChannels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final Executor ragRetrievalExecutor;

    public MultiChannelRetrievalEngine(List<SearchChannel> searchChannels,
                                       List<SearchResultPostProcessor> postProcessors,
                                       @Qualifier("ragRetrievalThreadPoolExecutor") Executor ragRetrievalExecutor) {
        this.searchChannels = searchChannels == null ? List.of() : List.copyOf(searchChannels);
        this.postProcessors = postProcessors == null ? List.of() : List.copyOf(postProcessors);
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
                .sorted(Comparator.comparingInt(SearchChannel::getPriority))
                .toList();
        if (enabledChannels.isEmpty()) {
            return List.of();
        }

        log.info("启用的检索通道：{}", enabledChannels.stream().map(SearchChannel::getName).toList());

        List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
                .map(channel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("执行检索通道：{}", channel.getName());
                        return channel.search(context);
                    } catch (Exception exception) {
                        log.error("检索通道 {} 执行失败", channel.getName(), exception);
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
                .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder))
                .toList();

        List<RetrievedChunk> chunks = results.stream()
                .filter(Objects::nonNull)
                .flatMap(result -> result.getChunks() == null ? List.<RetrievedChunk>of().stream() : result.getChunks().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        if (enabledProcessors.isEmpty()) {
            return chunks;
        }

        for (SearchResultPostProcessor processor : enabledProcessors) {
            try {
                List<RetrievedChunk> processed = processor.process(chunks, results, context);
                if (processed != null) {
                    chunks = new ArrayList<>(processed);
                }
                log.info("后置处理器 {} 完成，输出 Chunk 数：{}", processor.getName(), chunks.size());
            } catch (Exception exception) {
                log.error("后置处理器 {} 执行失败，跳过该处理器", processor.getName(), exception);
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
        String question = "";
        if (CollUtil.isNotEmpty(subIntents)) {
            question = subIntents.stream()
                    .filter(Objects::nonNull)
                    .map(SubQuestionIntent::subQuestion)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("");
        }

        return SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .subQuestions(CollUtil.isEmpty(subIntents)
                        ? List.of()
                        : subIntents.stream()
                                .filter(Objects::nonNull)
                                .map(SubQuestionIntent::subQuestion)
                                .filter(Objects::nonNull)
                                .toList())
                .intents(subIntents)
                .topK(Math.max(topK, 1))
                .metadata(new java.util.HashMap<>())
                .build();
    }

    private SearchContext normalizeContext(SearchContext context) {
        if (context == null) {
            return SearchContext.builder()
                    .originalQuestion("")
                    .rewrittenQuestion("")
                    .subQuestions(List.of())
                    .intents(List.of())
                    .topK(1)
                    .metadata(new java.util.HashMap<>())
                    .build();
        }

        return SearchContext.builder()
                .originalQuestion(context.getOriginalQuestion())
                .rewrittenQuestion(context.getRewrittenQuestion())
                .subQuestions(context.getSubQuestions() == null ? List.of() : List.copyOf(context.getSubQuestions()))
                .intents(context.getIntents() == null ? List.of() : List.copyOf(context.getIntents()))
                .topK(context.getTopK() <= 0 ? 1 : context.getTopK())
                .metadata(context.getMetadata() == null ? new java.util.HashMap<>() : new java.util.HashMap<>(context.getMetadata()))
                .build();
    }
}
