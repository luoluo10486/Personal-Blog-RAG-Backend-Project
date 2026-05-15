package com.personalblog.ragbackend.rag.core.retrieve.channel.strategy;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.AbstractParallelRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

public class CollectionParallelRetriever extends AbstractParallelRetriever<String> {
    private static final Logger log = LoggerFactory.getLogger(CollectionParallelRetriever.class);

    private final RetrieverService retrieverService;

    public CollectionParallelRetriever(RetrieverService retrieverService, Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    @Override
    protected List<RetrievedChunk> createRetrievalTask(String question, String collectionName, int topK) {
        if (StrUtil.isBlank(collectionName)) {
            return List.of();
        }
        try {
            return retrieverService.retrieve(RetrieveRequest.builder()
                    .collectionName(collectionName)
                    .query(question)
                    .topK(topK)
                    .build());
        } catch (Exception exception) {
            log.warn("collection retrieval failed for {}", collectionName, exception);
            return List.of();
        }
    }

    @Override
    protected String getTargetIdentifier(String collectionName) {
        return collectionName;
    }

    @Override
    protected String getStatisticsName() {
        return "collection";
    }
}
