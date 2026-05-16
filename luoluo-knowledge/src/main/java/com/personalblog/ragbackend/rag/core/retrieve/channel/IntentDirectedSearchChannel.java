package com.personalblog.ragbackend.rag.core.retrieve.channel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.NodeScoreFilters;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.strategy.IntentParallelRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class IntentDirectedSearchChannel implements SearchChannel {
    private final SearchChannelProperties properties;
    private final IntentParallelRetriever parallelRetriever;

    public IntentDirectedSearchChannel(RetrieverService retrieverService,
                                       SearchChannelProperties properties,
                                       @Qualifier("ragContextThreadPoolExecutor") Executor executor) {
        this.properties = properties;
        this.parallelRetriever = new IntentParallelRetriever(retrieverService, executor);
    }

    @Override
    public String getName() {
        return "IntentDirectedSearch";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        if (context == null) {
            return false;
        }
        if (!properties.getChannels().getIntentDirected().isEnabled()) {
            return false;
        }
        if (CollUtil.isEmpty(context.getIntents())) {
            return false;
        }
        return CollUtil.isNotEmpty(extractKbIntents(context));
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        if (context == null) {
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(List.of())
                    .latencyMs(0L)
                    .build();
        }
        long startTime = System.currentTimeMillis();
        try {
            List<NodeScore> kbIntents = extractKbIntents(context);
            if (CollUtil.isEmpty(kbIntents)) {
                log.info("意图定向检索通道未命中 KB 意图，跳过");
                return SearchChannelResult.builder()
                        .channelType(getType())
                        .channelName(getName())
                        .chunks(List.of())
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            int topKMultiplier = Math.max(properties.getChannels().getIntentDirected().getTopKMultiplier(), 1);
            String question = context.getMainQuestion();
            List<RetrievedChunk> chunks = parallelRetriever.executeParallelRetrieval(
                    question,
                    kbIntents,
                    Math.max(context.getTopK(), 1),
                    topKMultiplier
            );
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(chunks)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .metadata(java.util.Map.of("intentCount", kbIntents.size()))
                    .build();
        } catch (Exception exception) {
            log.error("意图定向检索通道执行失败", exception);
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(List.of())
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.INTENT_DIRECTED;
    }

    private List<NodeScore> extractKbIntents(SearchContext context) {
        if (context == null || CollUtil.isEmpty(context.getIntents())) {
            return List.of();
        }
        List<NodeScore> allScores = context.getIntents().stream()
                .flatMap(subQuestionIntent -> subQuestionIntent.nodeScores().stream())
                .toList();
        return NodeScoreFilters.kb(allScores, properties.getChannels().getIntentDirected().getMinIntentScore());
    }

}
