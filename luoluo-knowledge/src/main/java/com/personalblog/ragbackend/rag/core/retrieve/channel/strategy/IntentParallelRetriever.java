package com.personalblog.ragbackend.rag.core.retrieve.channel.strategy;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.AbstractParallelRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class IntentParallelRetriever extends AbstractParallelRetriever<IntentParallelRetriever.IntentTask> {
    private static final Logger log = LoggerFactory.getLogger(IntentParallelRetriever.class);

    private final RetrieverService retrieverService;

    public record IntentTask(NodeScore nodeScore, int intentTopK) {
    }

    public IntentParallelRetriever(RetrieverService retrieverService, Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    public List<RetrievedChunk> executeParallelRetrieval(String question,
                                                         List<NodeScore> targets,
                                                         int fallbackTopK,
                                                         int topKMultiplier) {
        List<IntentTask> intentTasks = new ArrayList<>();
        if (targets != null) {
            for (NodeScore nodeScore : targets) {
                intentTasks.add(new IntentTask(
                        nodeScore,
                        resolveIntentTopK(nodeScore, fallbackTopK, topKMultiplier)
                ));
            }
        }
        return super.executeParallelRetrieval(question, intentTasks, fallbackTopK);
    }

    public List<RetrievedChunk> executeParallelRetrieval(String question, List<IntentTask> tasks, int fallbackTopK) {
        List<IntentTask> normalized = tasks == null ? List.of() : tasks.stream()
                .map(task -> new IntentTask(task.nodeScore(), task.intentTopK() > 0 ? task.intentTopK() : fallbackTopK))
                .toList();
        return super.executeParallelRetrieval(question, normalized, fallbackTopK);
    }

    @Override
    protected List<RetrievedChunk> createRetrievalTask(String question, IntentTask task, int ignoredTopK) {
        if (task == null || task.nodeScore() == null || task.nodeScore().node() == null) {
            return List.of();
        }
        IntentNode node = task.nodeScore().node();
        String collectionName = StrUtil.blankToDefault(node.getCollectionName(), "");
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
            IntentNode failedNode = task.nodeScore().node();
            log.warn("intent retrieval failed for {}", failedNode == null ? "" : failedNode.getIntentCode(), exception);
            return List.of();
        }
    }

    @Override
    protected String getTargetIdentifier(IntentTask task) {
        if (task == null || task.nodeScore() == null || task.nodeScore().node() == null) {
            return "";
        }
        IntentNode node = task.nodeScore().node();
        return String.format("Intent ID: %s, Name: %s", node.getIntentCode(), node.getName());
    }

    @Override
    protected String getStatisticsName() {
        return "intent";
    }

    private int resolveIntentTopK(NodeScore nodeScore, int fallbackTopK, int topKMultiplier) {
        int baseTopK = fallbackTopK;
        if (nodeScore != null && nodeScore.node() != null) {
            Integer nodeTopK = nodeScore.node().getTopK();
            if (nodeTopK != null && nodeTopK > 0) {
                baseTopK = nodeTopK;
            }
        }
        if (topKMultiplier <= 0) {
            log.warn("intent topK multiplier invalid: {}, using base topK: {}", topKMultiplier, baseTopK);
            return baseTopK;
        }
        return baseTopK * topKMultiplier;
    }
}
