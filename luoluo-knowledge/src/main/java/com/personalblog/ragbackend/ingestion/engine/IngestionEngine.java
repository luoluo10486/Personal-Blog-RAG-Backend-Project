package com.personalblog.ragbackend.ingestion.engine;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.context.NodeLog;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionStatus;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.pipeline.PipelineDefinition;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.node.IngestionNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class IngestionEngine {

    private final Map<String, IngestionNode> nodeMap;
    private final ConditionEvaluator conditionEvaluator;
    private final NodeOutputExtractor outputExtractor;

    public IngestionEngine(List<IngestionNode> nodes,
                           ConditionEvaluator conditionEvaluator,
                           NodeOutputExtractor outputExtractor) {
        this.nodeMap = nodes.stream().collect(Collectors.toMap(IngestionNode::getNodeType, node -> node, (left, right) -> left));
        this.conditionEvaluator = conditionEvaluator;
        this.outputExtractor = outputExtractor;
    }

    public IngestionContext execute(PipelineDefinition pipeline, IngestionContext context) {
        if (context.getLogs() == null) {
            context.setLogs(new ArrayList<>());
        }
        context.setStatus(IngestionStatus.RUNNING);

        Map<String, NodeConfig> nodeConfigMap = buildNodeConfigMap(pipeline == null ? null : pipeline.getNodes());
        validatePipeline(nodeConfigMap);

        String startNodeId = findStartNode(nodeConfigMap);
        if (StrUtil.isBlank(startNodeId)) {
            throw new IllegalArgumentException("pipeline did not find a start node");
        }

        executeChain(startNodeId, nodeConfigMap, context);

        if (context.getStatus() == IngestionStatus.RUNNING) {
            context.setStatus(IngestionStatus.COMPLETED);
        }
        return context;
    }

    private Map<String, NodeConfig> buildNodeConfigMap(List<NodeConfig> nodes) {
        if (nodes == null) {
            return Collections.emptyMap();
        }
        Map<String, NodeConfig> result = new HashMap<>();
        for (NodeConfig node : nodes) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            result.putIfAbsent(node.getNodeId(), node);
        }
        return result;
    }

    private void validatePipeline(Map<String, NodeConfig> nodeConfigMap) {
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodeConfigMap.keySet()) {
            if (visited.contains(nodeId)) {
                continue;
            }
            Set<String> path = new HashSet<>();
            String current = nodeId;
            while (current != null) {
                if (path.contains(current)) {
                    throw new IllegalArgumentException("pipeline has cycle: " + current);
                }
                path.add(current);
                visited.add(current);
                NodeConfig config = nodeConfigMap.get(current);
                if (config == null) {
                    break;
                }
                String nextId = config.getNextNodeId();
                if (StringUtils.hasText(nextId)) {
                    if (!nodeConfigMap.containsKey(nextId)) {
                        throw new IllegalArgumentException("cannot find next node " + nextId + " referenced by " + current);
                    }
                    current = nextId;
                } else {
                    break;
                }
            }
        }
    }

    private String findStartNode(Map<String, NodeConfig> nodeConfigMap) {
        Set<String> referencedNodes = nodeConfigMap.values().stream()
                .map(NodeConfig::getNextNodeId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return nodeConfigMap.keySet().stream()
                .filter(nodeId -> !referencedNodes.contains(nodeId))
                .findFirst()
                .orElse(null);
    }

    private void executeChain(String nodeId, Map<String, NodeConfig> nodeConfigMap, IngestionContext context) {
        String currentNodeId = nodeId;
        int executedCount = 0;
        int maxNodes = nodeConfigMap.size();

        while (currentNodeId != null) {
            if (executedCount++ > maxNodes) {
                throw new IllegalArgumentException("pipeline execution count exceeded");
            }
            NodeConfig config = nodeConfigMap.get(currentNodeId);
            if (config == null) {
                log.warn("node config not found: {}", currentNodeId);
                break;
            }
            NodeResult result = executeNode(context, config);
            if (!result.isSuccess()) {
                context.setStatus(IngestionStatus.FAILED);
                context.setError(result.getError());
                break;
            }
            if (!result.isShouldContinue()) {
                break;
            }
            currentNodeId = config.getNextNodeId();
        }
    }

    private NodeResult executeNode(IngestionContext context, NodeConfig nodeConfig) {
        String nodeType = nodeConfig.getNodeType();
        String nodeId = nodeConfig.getNodeId();
        IngestionNode node = nodeMap.get(nodeType);
        if (node == null) {
            return NodeResult.fail(new IllegalStateException("node type not found: " + nodeType));
        }

        if (nodeConfig.getCondition() != null && !nodeConfig.getCondition().isNull()) {
            if (!conditionEvaluator.evaluate(context, nodeConfig.getCondition())) {
                NodeResult skip = NodeResult.skip("condition not matched");
                context.getLogs().add(NodeLog.builder()
                        .nodeId(nodeId)
                        .nodeType(nodeType)
                        .message(skip.getMessage())
                        .durationMs(0)
                        .success(true)
                        .output(outputExtractor.extract(context, nodeConfig))
                        .build());
                return skip;
            }
        }

        long start = System.currentTimeMillis();
        try {
            NodeResult result = node.execute(context, nodeConfig);
            long duration = System.currentTimeMillis() - start;
            context.getLogs().add(NodeLog.builder()
                    .nodeId(nodeId)
                    .nodeType(nodeType)
                    .message(result.getMessage())
                    .durationMs(duration)
                    .success(result.isSuccess())
                    .error(result.getError() == null ? null : result.getError().getMessage())
                    .output(outputExtractor.extract(context, nodeConfig))
                    .build());
            return result;
        } catch (Exception exception) {
            long duration = System.currentTimeMillis() - start;
            context.getLogs().add(NodeLog.builder()
                    .nodeId(nodeId)
                    .nodeType(nodeType)
                    .message(exception.getMessage())
                    .durationMs(duration)
                    .success(false)
                    .error(exception.getMessage())
                    .output(outputExtractor.extract(context, nodeConfig))
                    .build());
            return NodeResult.fail(exception);
        }
    }
}
