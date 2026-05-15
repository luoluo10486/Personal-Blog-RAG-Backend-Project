package com.personalblog.ragbackend.rag.core.retrieve.channel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.NodeScoreFilters;
import com.personalblog.ragbackend.rag.core.intent.RagIntentNode;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.strategy.IntentParallelRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

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
                return SearchChannelResult.builder()
                        .channelType(getType())
                        .channelName(getName())
                        .chunks(List.of())
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            int topKMultiplier = Math.max(properties.getChannels().getIntentDirected().getTopKMultiplier(), 1);
            List<IntentParallelRetriever.IntentTask> tasks = kbIntents.stream()
                    .map(nodeScore -> new IntentParallelRetriever.IntentTask(
                            nodeScore,
                            resolveCollectionName(nodeScore),
                            resolveTopK(nodeScore, context.getTopK(), topKMultiplier)
                    ))
                    .toList();
            List<RetrievedChunk> chunks = parallelRetriever.executeParallelRetrieval(context.getMainQuestion(), tasks, context.getTopK());
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(chunks)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .metadata(java.util.Map.of("intentCount", kbIntents.size()))
                    .build();
        } catch (Exception exception) {
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

    private String resolveCollectionName(NodeScore nodeScore) {
        if (nodeScore == null || nodeScore.node() == null) {
            return "";
        }
        RagIntentNode node = nodeScore.node();
        if (StrUtil.isNotBlank(node.getCollectionName())) {
            return node.getCollectionName().trim();
        }
        return "";
    }

    private int resolveTopK(NodeScore nodeScore, int fallbackTopK, int multiplier) {
        if (nodeScore == null || nodeScore.node() == null || nodeScore.node().getTopK() == null || nodeScore.node().getTopK() <= 0) {
            return fallbackTopK * multiplier;
        }
        return nodeScore.node().getTopK() * multiplier;
    }
}
