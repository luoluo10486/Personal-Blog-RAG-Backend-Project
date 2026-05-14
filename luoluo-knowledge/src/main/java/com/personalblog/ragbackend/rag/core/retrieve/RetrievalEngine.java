package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.RagIntentNode;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.mcp.MCPParameterExtractor;
import com.personalblog.ragbackend.rag.core.mcp.MCPRequest;
import com.personalblog.ragbackend.rag.core.mcp.MCPResponse;
import com.personalblog.ragbackend.rag.core.mcp.MCPTool;
import com.personalblog.ragbackend.rag.core.mcp.MCPToolExecutor;
import com.personalblog.ragbackend.rag.core.mcp.MCPToolRegistry;
import com.personalblog.ragbackend.rag.core.mcp.McpContextFormatter;
import com.personalblog.ragbackend.rag.core.prompt.KnowledgeContextFormatter;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class RetrievalEngine {
    private final KnowledgeRetrievalEngine knowledgeRetrievalEngine;
    private final KnowledgeContextFormatter knowledgeContextFormatter;
    private final MCPParameterExtractor mcpParameterExtractor;
    private final McpContextFormatter mcpContextFormatter;
    private final MCPToolRegistry mcpToolRegistry;
    private final Executor ragContextExecutor;
    private final Executor mcpBatchExecutor;

    public RetrievalEngine(KnowledgeRetrievalEngine knowledgeRetrievalEngine,
                           KnowledgeContextFormatter knowledgeContextFormatter,
                           MCPParameterExtractor mcpParameterExtractor,
                           McpContextFormatter mcpContextFormatter,
                           MCPToolRegistry mcpToolRegistry,
                           @Qualifier("ragContextThreadPoolExecutor") Executor ragContextExecutor,
                           @Qualifier("mcpBatchThreadPoolExecutor") Executor mcpBatchExecutor) {
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
        this.knowledgeContextFormatter = knowledgeContextFormatter;
        this.mcpParameterExtractor = mcpParameterExtractor;
        this.mcpContextFormatter = mcpContextFormatter;
        this.mcpToolRegistry = mcpToolRegistry;
        this.ragContextExecutor = ragContextExecutor;
        this.mcpBatchExecutor = mcpBatchExecutor;
    }

    @RagTraceNode(name = "retrieval-engine", type = "RETRIEVE")
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents,
                                     String fallbackBaseCode,
                                     int topK,
                                     String conversationId,
                                     String userId) {
        if (CollUtil.isEmpty(subIntents)) {
            return RetrievalContext.empty();
        }

        int finalTopK = topK > 0 ? topK : 5;
        List<CompletableFuture<SubQuestionContext>> tasks = subIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(
                        () -> buildSubQuestionContext(intent, fallbackBaseCode, finalTopK, conversationId, userId),
                        ragContextExecutor
                ))
                .toList();

        List<SubQuestionContext> contexts = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        StringBuilder kbBuilder = new StringBuilder();
        StringBuilder mcpBuilder = new StringBuilder();
        Map<String, List<RetrievedChunk>> mergedIntentChunks = new LinkedHashMap<>();
        for (SubQuestionContext context : contexts) {
            if (StrUtil.isNotBlank(context.kbContext())) {
                appendSection(kbBuilder, context.question(), context.kbContext());
            }
            if (StrUtil.isNotBlank(context.mcpContext())) {
                appendSection(mcpBuilder, context.question(), context.mcpContext());
            }
            context.intentChunks().forEach((key, chunks) -> {
                if (CollUtil.isNotEmpty(chunks)) {
                    mergedIntentChunks.put(key, chunks);
                }
            });
        }

        return new RetrievalContext(
                mcpBuilder.toString().trim(),
                kbBuilder.toString().trim(),
                mergedIntentChunks
        );
    }

    private SubQuestionContext buildSubQuestionContext(SubQuestionIntent intent,
                                                       String fallbackBaseCode,
                                                       int fallbackTopK,
                                                       String conversationId,
                                                       String userId) {
        List<NodeScore> kbIntents = filterKb(intent.nodeScores());
        List<NodeScore> mcpIntents = filterMcp(intent.nodeScores());
        int topK = resolveSubQuestionTopK(kbIntents, fallbackTopK);

        KbResult kbResult = CollUtil.isNotEmpty(kbIntents) || CollUtil.isEmpty(mcpIntents)
                ? retrieveKnowledge(intent.subQuestion(), kbIntents, fallbackBaseCode, topK)
                : KbResult.empty();
        String mcpContext = CollUtil.isEmpty(mcpIntents)
                ? ""
                : executeMcpAndFormat(intent.subQuestion(), mcpIntents, fallbackBaseCode, topK, conversationId, userId);
        return new SubQuestionContext(intent.subQuestion(), kbResult.context(), mcpContext, kbResult.intentChunks());
    }

    private KbResult retrieveKnowledge(String question,
                                       List<NodeScore> kbIntents,
                                       String fallbackBaseCode,
                                       int topK) {
        String baseCode = selectBaseCode(fallbackBaseCode, kbIntents);
        List<RetrievedChunk> chunks = knowledgeRetrievalEngine.retrieve(new RetrieveRequest(baseCode, question, topK));
        if (CollUtil.isEmpty(chunks)) {
            return KbResult.empty();
        }

        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        if (CollUtil.isEmpty(kbIntents)) {
            intentChunks.put("multi_channel", chunks);
        } else {
            for (NodeScore intent : kbIntents) {
                String key = nodeKey(intent.node());
                if (StrUtil.isNotBlank(key)) {
                    intentChunks.put(key, chunks);
                }
            }
        }
        String context = knowledgeContextFormatter.formatKbContext(kbIntents, chunks);
        return new KbResult(context, intentChunks);
    }

    private String executeMcpAndFormat(String question,
                                       List<NodeScore> mcpIntents,
                                       String baseCode,
                                       int topK,
                                       String conversationId,
                                       String userId) {
        List<MCPResponse> responses = executeMcpTools(question, mcpIntents, baseCode, topK, conversationId, userId);
        return mcpContextFormatter.format(responses, mcpIntents);
    }

    private List<MCPResponse> executeMcpTools(String question,
                                              List<NodeScore> mcpIntents,
                                              String baseCode,
                                              int topK,
                                              String conversationId,
                                              String userId) {
        List<CompletableFuture<MCPResponse>> futures = mcpIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(
                        () -> executeSingleMcpTool(question, intent, baseCode, topK, conversationId, userId),
                        mcpBatchExecutor
                ))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private MCPResponse executeSingleMcpTool(String question,
                                             NodeScore intent,
                                             String baseCode,
                                             int topK,
                                             String conversationId,
                                             String userId) {
        RagIntentNode node = intent == null ? null : intent.node();
        if (node == null || StrUtil.isBlank(node.mcpToolId)) {
            return MCPResponse.error("", "MISSING_TOOL", "Missing MCP tool id");
        }
        String toolId = node.mcpToolId.trim();
        Optional<MCPToolExecutor> executorOpt = mcpToolRegistry.getExecutor(toolId);
        if (executorOpt.isEmpty()) {
            return MCPResponse.error(toolId, "TOOL_NOT_FOUND", "MCP tool not found: " + toolId);
        }
        MCPToolExecutor executor = executorOpt.get();
        Map<String, Object> params = mcpParameterExtractor.extractParameters(
                question,
                executor.getToolDefinition(),
                node.paramPromptTemplate
        );
        return executor.execute(MCPRequest.builder()
                .toolId(toolId)
                .userQuestion(question)
                .parameters(params)
                .userId(userId)
                .conversationId(conversationId)
                .build());
    }

    private List<NodeScore> filterKb(List<NodeScore> nodeScores) {
        if (CollUtil.isEmpty(nodeScores)) {
            return List.of();
        }
        return nodeScores.stream()
                .filter(score -> score != null && score.node() != null && score.node().isKb())
                .toList();
    }

    private List<NodeScore> filterMcp(List<NodeScore> nodeScores) {
        if (CollUtil.isEmpty(nodeScores)) {
            return List.of();
        }
        return nodeScores.stream()
                .filter(score -> score != null && score.node() != null && score.node().isMcp())
                .toList();
    }

    private int resolveSubQuestionTopK(List<NodeScore> kbIntents, int fallbackTopK) {
        return kbIntents.stream()
                .map(NodeScore::node)
                .filter(Objects::nonNull)
                .map(node -> node.topK)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(fallbackTopK);
    }

    private String selectBaseCode(String fallbackBaseCode, List<NodeScore> kbIntents) {
        return kbIntents.stream()
                .map(NodeScore::node)
                .filter(Objects::nonNull)
                .map(node -> firstNotBlank(node.collectionName, node.intentCode, node.name))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(StrUtil.blankToDefault(fallbackBaseCode, ""));
    }

    private void appendSection(StringBuilder builder, String question, String context) {
        builder.append("---\n")
                .append("**Sub-question**: ").append(StrUtil.blankToDefault(question, "")).append("\n\n")
                .append(context)
                .append("\n\n");
    }

    private String nodeKey(RagIntentNode node) {
        if (node == null) {
            return "";
        }
        if (node.id != null) {
            return String.valueOf(node.id);
        }
        return firstNotBlank(node.intentCode, node.collectionName, node.name);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record KbResult(String context, Map<String, List<RetrievedChunk>> intentChunks) {
        private static KbResult empty() {
            return new KbResult("", Map.of());
        }
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks) {
    }
}
