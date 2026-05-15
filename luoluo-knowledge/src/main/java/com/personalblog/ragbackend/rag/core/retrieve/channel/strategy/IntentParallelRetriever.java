package com.personalblog.ragbackend.rag.core.retrieve.channel.strategy;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.RagIntentNode;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.AbstractParallelRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

public class IntentParallelRetriever extends AbstractParallelRetriever<IntentParallelRetriever.IntentTask> {
    private static final Logger log = LoggerFactory.getLogger(IntentParallelRetriever.class);

    private final RetrieverService retrieverService;

    public record IntentTask(NodeScore nodeScore, String collectionName, int intentTopK) {
    }

    public IntentParallelRetriever(RetrieverService retrieverService, Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    public List<RetrievedChunk> executeParallelRetrieval(String question, List<IntentTask> tasks, int fallbackTopK) {
        List<IntentTask> normalized = tasks == null ? List.of() : tasks.stream()
                .map(task -> new IntentTask(task.nodeScore(), task.collectionName(), task.intentTopK() > 0 ? task.intentTopK() : fallbackTopK))
                .toList();
        return super.executeParallelRetrieval(question, normalized, fallbackTopK);
    }

    @Override
    protected List<RetrievedChunk> createRetrievalTask(String question, IntentTask task, int ignoredTopK) {
        if (task == null || task.nodeScore() == null || task.nodeScore().node() == null) {
            return List.of();
        }
        String collectionName = StrUtil.blankToDefault(task.collectionName(), "");
        if (StrUtil.isBlank(collectionName)) {
            RagIntentNode node = task.nodeScore().node();
            collectionName = StrUtil.blankToDefault(node.getCollectionName(), "");
        }
        if (StrUtil.isBlank(collectionName)) {
            return List.of();
        }
        try {
            return retrieverService.retrieve(RetrieveRequest.builder()
                    .collectionName(collectionName)
                    .query(question)
                    .topK(task.intentTopK())
                    .build());
        } catch (Exception exception) {
            RagIntentNode node = task.nodeScore().node();
            log.warn("intent retrieval failed for {}", node == null ? "" : node.getIntentCode(), exception);
            return List.of();
        }
    }

    @Override
    protected String getTargetIdentifier(IntentTask task) {
        if (task == null || task.nodeScore() == null || task.nodeScore().node() == null) {
            return "";
        }
        RagIntentNode node = task.nodeScore().node();
        return StrUtil.blankToDefault(node.getIntentCode(), StrUtil.blankToDefault(node.getName(), ""));
    }

    @Override
    protected String getStatisticsName() {
        return "intent";
    }
}
