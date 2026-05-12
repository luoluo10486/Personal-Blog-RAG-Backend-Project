package com.personalblog.ragbackend.knowledge.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.service.ingest.pipeline.IngestionPipelineDefinition;
import com.personalblog.ragbackend.knowledge.service.ingest.pipeline.IngestionPipelineNodeConfig;
import com.personalblog.ragbackend.knowledge.trace.RagTraceRoot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KnowledgeIngestionEngine {
    private final List<KnowledgeIngestionNode> nodes;
    private final Map<String, KnowledgeIngestionNode> nodeMap;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionEngine(List<KnowledgeIngestionNode> nodes, ObjectMapper objectMapper) {
        this.nodes = nodes.stream()
                .sorted(Comparator.comparingInt(KnowledgeIngestionNode::getOrder))
                .toList();
        this.nodeMap = this.nodes.stream()
                .collect(Collectors.toMap(node -> normalizeNodeType(node.getNodeType()), Function.identity(), (left, right) -> left));
        this.objectMapper = objectMapper;
    }

    @RagTraceRoot(name = "knowledge-ingestion", taskIdArg = "taskId")
    public KnowledgeIngestionResult execute(KnowledgeIngestionRequest request) {
        KnowledgeIngestionContext context = new KnowledgeIngestionContext(
                request.baseCode(),
                request.file(),
                request.mode(),
                request.pipelineId(),
                request.taskId(),
                request.sourceType(),
                request.sourceLocation(),
                request.sourceFileName(),
                request.sourceFileUrl()
        );
        for (KnowledgeIngestionNode node : nodes) {
            executeNode(context, node, node.getNodeType(), node.getOrder());
            if (hasFailure(context)) {
                break;
            }
        }
        return toResult(context);
    }

    @RagTraceRoot(name = "knowledge-ingestion", taskIdArg = "taskId")
    public KnowledgeIngestionResult execute(IngestionPipelineDefinition pipeline, KnowledgeIngestionRequest request) {
        KnowledgeIngestionContext context = new KnowledgeIngestionContext(
                request.baseCode(),
                request.file(),
                request.mode(),
                request.pipelineId(),
                request.taskId(),
                request.sourceType(),
                request.sourceLocation(),
                request.sourceFileName(),
                request.sourceFileUrl()
        );
        List<IngestionPipelineNodeConfig> executionPlan = expandExecutionPlan(pipeline);
        for (int i = 0; i < executionPlan.size(); i++) {
            IngestionPipelineNodeConfig config = executionPlan.get(i);
            String mappedNodeType = normalizeNodeType(mapPipelineNodeType(config.nodeType()));
            KnowledgeIngestionNode node = nodeMap.get(mappedNodeType);
            if (node == null) {
                if (isNoopNodeType(mappedNodeType)) {
                    executeNoopNode(context, config, i + 1);
                    continue;
                }
                context.setIngestionSummary(DocumentIngestionSummary.failure(
                        request.baseCode(),
                        "Node [" + config.nodeType() + "] is not supported"
                ));
                context.addNodeLog(new KnowledgeIngestionNodeLog(
                        config.nodeId(),
                        normalizeNodeType(config.nodeType()),
                        i + 1,
                        "failed",
                        0,
                        null,
                        "Unsupported node type: " + config.nodeType(),
                        buildNodeOutput(context)
                ));
                break;
            }
            executeNode(context, node, config.nodeId(), i + 1);
            if (hasFailure(context)) {
                break;
            }
        }
        return toResult(context);
    }

    private void executeNoopNode(KnowledgeIngestionContext context,
                                 IngestionPipelineNodeConfig config,
                                 int nodeOrder) {
        context.addNodeLog(new KnowledgeIngestionNodeLog(
                config.nodeId(),
                normalizeNodeType(config.nodeType()),
                nodeOrder,
                "success",
                0,
                "No-op node completed successfully",
                null,
                buildNodeOutput(context)
        ));
    }

    private void executeNode(KnowledgeIngestionContext context,
                             KnowledgeIngestionNode node,
                             String nodeId,
                             int nodeOrder) {
        long startedAt = System.currentTimeMillis();
        boolean failedBefore = hasFailure(context);
        String status = "success";
        String errorMessage = null;
        try {
            node.execute(context);
            if (failedBefore) {
                status = "skipped";
            } else if (hasFailure(context)) {
                status = "failed";
                errorMessage = context.getIngestionSummary() == null ? null : context.getIngestionSummary().errorMessage();
            }
        } catch (RuntimeException exception) {
            status = "failed";
            errorMessage = exception.getMessage();
            if (!hasFailure(context)) {
                context.setIngestionSummary(DocumentIngestionSummary.failure(
                        context.getBaseCode(),
                        "Node [" + node.getNodeType() + "] failed: " + exception.getMessage()
                ));
            }
        }
        context.addNodeLog(new KnowledgeIngestionNodeLog(
                nodeId,
                node.getNodeType(),
                nodeOrder,
                status,
                System.currentTimeMillis() - startedAt,
                resolveMessage(context, status),
                errorMessage,
                buildNodeOutput(context)
        ));
    }

    private KnowledgeIngestionResult toResult(KnowledgeIngestionContext context) {
        return new KnowledgeIngestionResult(
                context.getPlan(),
                context.getParseResult(),
                context.getChunkResponse(),
                context.getIngestionSummary(),
                context.getNodeLogs()
        );
    }

    private List<IngestionPipelineNodeConfig> expandExecutionPlan(IngestionPipelineDefinition pipeline) {
        List<IngestionPipelineNodeConfig> chain = resolvePipelineChain(pipeline);
        List<IngestionPipelineNodeConfig> expanded = new ArrayList<>();
        Set<String> addedTypes = new HashSet<>();
        for (IngestionPipelineNodeConfig config : chain) {
            String normalizedType = normalizeNodeType(config.nodeType());
            List<String> localTypes = mapPipelineNodeTypes(normalizedType);
            for (String localType : localTypes) {
                if (addedTypes.add(localType)) {
                    expanded.add(new IngestionPipelineNodeConfig(
                            config.nodeId(),
                            localType,
                            config.settings(),
                            config.condition(),
                            config.nextNodeId()
                    ));
                }
            }
        }
        return expanded;
    }

    private List<IngestionPipelineNodeConfig> resolvePipelineChain(IngestionPipelineDefinition pipeline) {
        if (pipeline == null || pipeline.nodes() == null || pipeline.nodes().isEmpty()) {
            return defaultPipeline();
        }
        Map<String, IngestionPipelineNodeConfig> byId = new LinkedHashMap<>();
        for (IngestionPipelineNodeConfig node : pipeline.nodes()) {
            if (node != null && StringUtils.hasText(node.nodeId())) {
                byId.putIfAbsent(node.nodeId(), node);
            }
        }
        if (byId.isEmpty()) {
            return defaultPipeline();
        }
        validatePipeline(byId);
        String startNodeId = findStartNode(byId);
        if (!StringUtils.hasText(startNodeId)) {
            throw new IllegalArgumentException("Pipeline start node not found");
        }
        List<IngestionPipelineNodeConfig> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = startNodeId;
        while (StringUtils.hasText(current) && !visited.contains(current)) {
            IngestionPipelineNodeConfig config = byId.get(current);
            if (config == null) {
                break;
            }
            chain.add(config);
            visited.add(current);
            current = config.nextNodeId();
        }
        for (Map.Entry<String, IngestionPipelineNodeConfig> entry : byId.entrySet()) {
            if (!visited.contains(entry.getKey())) {
                chain.add(entry.getValue());
            }
        }
        return chain;
    }

    private void validatePipeline(Map<String, IngestionPipelineNodeConfig> byId) {
        for (String nodeId : byId.keySet()) {
            Set<String> path = new HashSet<>();
            String current = nodeId;
            while (StringUtils.hasText(current)) {
                if (!path.add(current)) {
                    throw new IllegalArgumentException("Pipeline has cycle: " + current);
                }
                IngestionPipelineNodeConfig config = byId.get(current);
                if (config == null || !StringUtils.hasText(config.nextNodeId())) {
                    break;
                }
                if (!byId.containsKey(config.nextNodeId())) {
                    throw new IllegalArgumentException("Pipeline next node not found: " + config.nextNodeId());
                }
                current = config.nextNodeId();
            }
        }
    }

    private String findStartNode(Map<String, IngestionPipelineNodeConfig> byId) {
        Set<String> referenced = byId.values().stream()
                .map(IngestionPipelineNodeConfig::nextNodeId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return byId.keySet().stream()
                .filter(nodeId -> !referenced.contains(nodeId))
                .findFirst()
                .orElse(null);
    }

    private List<IngestionPipelineNodeConfig> defaultPipeline() {
        return List.of(
                new IngestionPipelineNodeConfig("plan", "plan", Map.of(), Map.of(), "parse"),
                new IngestionPipelineNodeConfig("parse", "parse", Map.of(), Map.of(), "chunk"),
                new IngestionPipelineNodeConfig("enhancer", "enhancer", Map.of(), Map.of(), "chunk"),
                new IngestionPipelineNodeConfig("chunk", "chunk", Map.of(), Map.of(), "persist"),
                new IngestionPipelineNodeConfig("persist", "persist", Map.of(), Map.of(), "embed"),
                new IngestionPipelineNodeConfig("embed", "embed", Map.of(), Map.of(), "index"),
                new IngestionPipelineNodeConfig("enricher", "enricher", Map.of(), Map.of(), "index"),
                new IngestionPipelineNodeConfig("index", "index", Map.of(), Map.of(), "finalize"),
                new IngestionPipelineNodeConfig("finalize", "finalize", Map.of(), Map.of(), null)
        );
    }

    private List<String> mapPipelineNodeTypes(String nodeType) {
        return switch (normalizeNodeType(nodeType)) {
            case "fetcher" -> List.of("plan");
            case "parser" -> List.of("parse");
            case "enhancer" -> List.of("enhancer");
            case "chunker" -> List.of("chunk", "persist", "embed");
            case "enricher" -> List.of("enricher");
            case "indexer" -> List.of("index", "finalize");
            default -> List.of(mapPipelineNodeType(nodeType));
        };
    }

    private String mapPipelineNodeType(String nodeType) {
        return switch (normalizeNodeType(nodeType)) {
            case "fetcher" -> "plan";
            case "parser" -> "parse";
            case "enhancer" -> "enhancer";
            case "chunker" -> "chunk";
            case "enricher" -> "enricher";
            case "indexer" -> "index";
            default -> normalizeNodeType(nodeType);
        };
    }

    private boolean isNoopNodeType(String nodeType) {
        return "enhancer".equals(normalizeNodeType(nodeType)) || "enricher".equals(normalizeNodeType(nodeType));
    }

    private String normalizeNodeType(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return "";
        }
        return nodeType.trim().toLowerCase().replace("-", "_");
    }

    private boolean hasFailure(KnowledgeIngestionContext context) {
        return context.getIngestionSummary() != null && !context.getIngestionSummary().success();
    }

    private String resolveMessage(KnowledgeIngestionContext context, String status) {
        if ("failed".equals(status) && context.getIngestionSummary() != null) {
            return context.getIngestionSummary().errorMessage();
        }
        if ("skipped".equals(status)) {
            return "Skipped because the ingestion context has already failed";
        }
        if (context.getIngestionSummary() != null && context.getIngestionSummary().success()) {
            return "Ingestion finished successfully";
        }
        return "OK";
    }

    private String buildNodeOutput(KnowledgeIngestionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", context.getTaskId());
        output.put("pipelineId", context.getPipelineId());
        output.put("planReady", context.getPlan() != null);
        output.put("parseSuccess", context.getParseResult() != null && context.getParseResult().success());
        output.put("chunkSuccess", context.getChunkResponse() != null && context.getChunkResponse().success());
        output.put("persistedChunkCount", context.getPersistedChunks().size());
        output.put("vectorIndexed", context.isVectorIndexed());
        output.put("summarySuccess", context.getIngestionSummary() != null && context.getIngestionSummary().success());
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception ignored) {
            return null;
        }
    }
}
