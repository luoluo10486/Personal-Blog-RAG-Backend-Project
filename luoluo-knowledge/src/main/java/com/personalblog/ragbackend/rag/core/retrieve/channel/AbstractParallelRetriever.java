package com.personalblog.ragbackend.rag.core.retrieve.channel;

import cn.hutool.core.collection.CollUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class AbstractParallelRetriever<T> {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final Executor executor;

    protected AbstractParallelRetriever(Executor executor) {
        this.executor = executor;
    }

    public List<RetrievedChunk> executeParallelRetrieval(String question, List<T> targets, int topK) {
        if (CollUtil.isEmpty(targets)) {
            return List.of();
        }

        List<CompletableFuture<List<RetrievedChunk>>> futures = new ArrayList<>(targets.size());
        for (T target : targets) {
            futures.add(CompletableFuture.supplyAsync(() -> createRetrievalTask(question, target, topK), executor));
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .sorted(Comparator.comparingDouble((RetrievedChunk chunk) -> chunk.getScore() == null ? 0D : chunk.getScore()).reversed())
                .toList();
    }

    protected abstract List<RetrievedChunk> createRetrievalTask(String question, T target, int topK);

    protected abstract String getTargetIdentifier(T target);

    protected abstract String getStatisticsName();
}
