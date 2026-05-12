package com.personalblog.ragbackend.knowledge.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.trace.RagTraceRoot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeIngestionEngine {
    private final List<KnowledgeIngestionNode> nodes;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionEngine(List<KnowledgeIngestionNode> nodes, ObjectMapper objectMapper) {
        this.nodes = nodes.stream()
                .sorted(Comparator.comparingInt(KnowledgeIngestionNode::getOrder))
                .toList();
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
            long startedAt = System.currentTimeMillis();
            boolean failedBefore = hasFailure(context);
            String status = "SUCCESS";
            String errorMessage = null;
            try {
                node.execute(context);
                if (failedBefore) {
                    status = "SKIPPED";
                } else if (hasFailure(context)) {
                    status = "FAILED";
                    errorMessage = context.getIngestionSummary() == null ? null : context.getIngestionSummary().errorMessage();
                }
            } catch (RuntimeException exception) {
                status = "FAILED";
                errorMessage = exception.getMessage();
                if (!hasFailure(context)) {
                    context.setIngestionSummary(DocumentIngestionSummary.failure(
                            request.baseCode(),
                            "Node [" + node.getNodeType() + "] failed: " + exception.getMessage()
                    ));
                }
                context.addNodeLog(new KnowledgeIngestionNodeLog(
                        node.getNodeType(),
                        node.getOrder(),
                        status,
                        System.currentTimeMillis() - startedAt,
                        null,
                        errorMessage,
                        buildNodeOutput(context)
                ));
                break;
            }
            context.addNodeLog(new KnowledgeIngestionNodeLog(
                    node.getNodeType(),
                    node.getOrder(),
                    status,
                    System.currentTimeMillis() - startedAt,
                    resolveMessage(context, status),
                    errorMessage,
                    buildNodeOutput(context)
            ));
        }
        return new KnowledgeIngestionResult(
                context.getPlan(),
                context.getParseResult(),
                context.getChunkResponse(),
                context.getIngestionSummary(),
                context.getNodeLogs()
        );
    }

    private boolean hasFailure(KnowledgeIngestionContext context) {
        return context.getIngestionSummary() != null && !context.getIngestionSummary().success();
    }

    private String resolveMessage(KnowledgeIngestionContext context, String status) {
        if ("FAILED".equals(status) && context.getIngestionSummary() != null) {
            return context.getIngestionSummary().errorMessage();
        }
        if ("SKIPPED".equals(status)) {
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
