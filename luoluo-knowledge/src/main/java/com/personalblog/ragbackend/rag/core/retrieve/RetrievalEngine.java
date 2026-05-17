package com.personalblog.ragbackend.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.NodeScoreFilters;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.mcp.MCPRequest;
import com.personalblog.ragbackend.rag.core.mcp.MCPResponse;
import com.personalblog.ragbackend.rag.core.mcp.McpParameterExtractor;
import com.personalblog.ragbackend.rag.core.mcp.McpToolExecutor;
import com.personalblog.ragbackend.rag.core.mcp.McpToolRegistry;
import com.personalblog.ragbackend.rag.core.prompt.ContextFormatter;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
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
import java.util.stream.Collectors;

@Service
public class RetrievalEngine {
    private final SearchChannelProperties searchProperties;
    private final ContextFormatter contextFormatter;
    private final PromptTemplateLoader promptTemplateLoader;
    private final McpParameterExtractor mcpParameterExtractor;
    private final McpToolRegistry mcpToolRegistry;
    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;
    private final Executor ragContextExecutor;
    private final Executor mcpBatchExecutor;

    public RetrievalEngine(SearchChannelProperties searchProperties,
                           ContextFormatter contextFormatter,
                           PromptTemplateLoader promptTemplateLoader,
                           McpParameterExtractor mcpParameterExtractor,
                           McpToolRegistry mcpToolRegistry,
                           MultiChannelRetrievalEngine multiChannelRetrievalEngine,
                           @Qualifier("ragContextExecutor") Executor ragContextExecutor,
                           @Qualifier("mcpBatchExecutor") Executor mcpBatchExecutor) {
        this.searchProperties = searchProperties;
        this.contextFormatter = contextFormatter;
        this.promptTemplateLoader = promptTemplateLoader;
        this.mcpParameterExtractor = mcpParameterExtractor;
        this.mcpToolRegistry = mcpToolRegistry;
        this.multiChannelRetrievalEngine = multiChannelRetrievalEngine;
        this.ragContextExecutor = ragContextExecutor;
        this.mcpBatchExecutor = mcpBatchExecutor;
    }

    @RagTraceNode(name = "retrieval-engine", type = "RETRIEVE")
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        if (CollUtil.isEmpty(subIntents)) {
            return RetrievalContext.empty();
        }

        int finalTopK = topK > 0 ? topK : searchProperties.getDefaultTopK();
        List<CompletableFuture<SubQuestionContext>> tasks = subIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return buildSubQuestionContext(intent, resolveSubQuestionTopK(intent, finalTopK));
                            } catch (Exception exception) {
                                return new SubQuestionContext(
                                        intent == null ? "" : intent.subQuestion(),
                                        "",
                                        "",
                                        Map.of()
                                );
                            }
                        },
                        ragContextExecutor
                ))
                .toList();

        List<SubQuestionContext> contexts = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        Map<String, List<RetrievedChunk>> mergedIntentChunks = new LinkedHashMap<>();
        for (SubQuestionContext context : contexts) {
            if (CollUtil.isNotEmpty(context.intentChunks())) {
                mergedIntentChunks.putAll(context.intentChunks());
            }
        }

        if (contexts.size() == 1) {
            SubQuestionContext only = contexts.get(0);
            return RetrievalContext.builder()
                    .mcpContext(StrUtil.emptyIfNull(only.mcpContext()).trim())
                    .kbContext(StrUtil.emptyIfNull(only.kbContext()).trim())
                    .intentChunks(mergedIntentChunks)
                    .build();
        }

        StringBuilder kbBuilder = new StringBuilder();
        StringBuilder mcpBuilder = new StringBuilder();
        int globalIndex = 0;
        for (SubQuestionContext context : contexts) {
            boolean hasKb = StrUtil.isNotBlank(context.kbContext());
            boolean hasMcp = StrUtil.isNotBlank(context.mcpContext());
            if (hasKb || hasMcp) {
                globalIndex++;
            }
            if (hasKb) {
                appendSection(kbBuilder, "sub-question-kb-wrapper", globalIndex, context.question(), context.kbContext());
            }
            if (hasMcp) {
                appendSection(mcpBuilder, "sub-question-mcp-wrapper", globalIndex, context.question(), context.mcpContext());
            }
        }

        return RetrievalContext.builder()
                .mcpContext(mcpBuilder.toString().trim())
                .kbContext(kbBuilder.toString().trim())
                .intentChunks(mergedIntentChunks)
                .build();
    }

    private SubQuestionContext buildSubQuestionContext(SubQuestionIntent intent, int topK) {
        if (intent == null) {
            return new SubQuestionContext("", "", "", Map.of());
        }
        List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());
        List<NodeScore> mcpIntents = NodeScoreFilters.mcp(intent.nodeScores());

        KbResult kbResult = retrieveAndRerank(intent, kbIntents, topK);
        String mcpContext = CollUtil.isNotEmpty(mcpIntents)
                ? executeMcpAndMerge(intent.subQuestion(), mcpIntents)
                : "";

        return new SubQuestionContext(intent.subQuestion(), kbResult.groupedContext(), mcpContext, kbResult.intentChunks());
    }

    private int resolveSubQuestionTopK(SubQuestionIntent intent, int fallbackTopK) {
        List<NodeScore> kbIntents = NodeScoreFilters.kb(intent == null ? List.of() : intent.nodeScores());
        return kbIntents.stream()
                .map(NodeScore::node)
                .filter(Objects::nonNull)
                .map(IntentNode::getTopK)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(fallbackTopK);
    }

    private KbResult retrieveAndRerank(SubQuestionIntent intent, List<NodeScore> kbIntents, int topK) {
        List<RetrievedChunk> chunks = multiChannelRetrievalEngine.retrieveKnowledgeChannels(List.of(intent), topK);
        if (CollUtil.isEmpty(chunks)) {
            return KbResult.empty();
        }

        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(kbIntents)) {
            for (NodeScore nodeScore : kbIntents) {
                String key = nodeKey(nodeScore.node());
                if (StrUtil.isNotBlank(key)) {
                    intentChunks.put(key, chunks);
                }
            }
        } else {
            intentChunks.put(RAGConstant.MULTI_CHANNEL_KEY, chunks);
        }

        String groupedContext = contextFormatter.formatKbContext(kbIntents, intentChunks, topK);
        return new KbResult(groupedContext, intentChunks);
    }

    private String executeMcpAndMerge(String question, List<NodeScore> mcpIntents) {
        Map<String, List<CallToolResult>> toolResults = executeMcpTools(question, mcpIntents);
        return contextFormatter.formatMcpContext(toolResults, mcpIntents);
    }

    private Map<String, List<CallToolResult>> executeMcpTools(String question, List<NodeScore> mcpIntents) {
        List<CompletableFuture<ToolResult>> futures = mcpIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                MCPResponse response = executeSingleMcpTool(question, intent);
                                return response == null ? null : new ToolResult(response.getToolId(), toCallToolResult(response));
                            } catch (Exception exception) {
                                IntentNode node = intent == null ? null : intent.node();
                                String toolId = node == null ? "" : StrUtil.blankToDefault(node.getMcpToolId(), "");
                                String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
                                return new ToolResult(toolId, CallToolResult.builder()
                                        .isError(true)
                                        .content(List.of(new TextContent(reason)))
                                        .build());
                            }
                        },
                        mcpBatchExecutor
                ))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ToolResult::toolId,
                        LinkedHashMap::new,
                        Collectors.mapping(ToolResult::result, Collectors.toList())
                ));
    }

    private MCPResponse executeSingleMcpTool(String question, NodeScore intent) {
        IntentNode node = intent == null ? null : intent.node();
        if (node == null || StrUtil.isBlank(node.getMcpToolId())) {
            return MCPResponse.error("", "MISSING_TOOL", "Missing MCP tool id");
        }
        String toolId = node.getMcpToolId().trim();
        Optional<McpToolExecutor> executorOpt = mcpToolRegistry.getExecutor(toolId);
        if (executorOpt.isEmpty()) {
            return MCPResponse.error(toolId, "TOOL_NOT_FOUND", "MCP tool not found: " + toolId);
        }
        McpToolExecutor executor = executorOpt.get();
        Map<String, Object> params = mcpParameterExtractor.extractParameters(
                question,
                executor.getToolDefinition(),
                node.getParamPromptTemplate()
        );
        return executor.execute(MCPRequest.builder()
                .toolId(toolId)
                .userQuestion(question)
                .parameters(params)
                .build());
    }

    private CallToolResult toCallToolResult(MCPResponse response) {
        String text = response.isSuccess()
                ? StrUtil.blankToDefault(response.getTextResult(), "")
                : StrUtil.blankToDefault(response.getErrorMessage(), "");
        return CallToolResult.builder()
                .isError(!response.isSuccess())
                .content(List.of(new TextContent(text)))
                .build();
    }

    private void appendSection(StringBuilder builder, String sectionName, int index, String question, String context) {
        String rendered = promptTemplateLoader.renderSection(
                RAGConstant.CONTEXT_FORMAT_PATH,
                sectionName,
                Map.of(
                        "index", String.valueOf(index),
                        "question", StrUtil.blankToDefault(question, ""),
                        "context", StrUtil.blankToDefault(context, "")
                )
        );
        if (StrUtil.isBlank(rendered)) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("---\n")
                    .append("**Sub-question**: ").append(StrUtil.blankToDefault(question, "")).append("\n\n")
                    .append(context)
                    .append("\n\n");
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(rendered);
    }

    private String nodeKey(IntentNode node) {
        if (node == null) {
            return "";
        }
        if (StrUtil.isNotBlank(node.getId())) {
            return node.getId();
        }
        if (StrUtil.isNotBlank(node.getIntentCode())) {
            return node.getIntentCode().trim();
        }
        if (StrUtil.isNotBlank(node.getCollectionName())) {
            return node.getCollectionName().trim();
        }
        return StrUtil.blankToDefault(node.getName(), "").trim();
    }

    private record KbResult(String groupedContext, Map<String, List<RetrievedChunk>> intentChunks) {
        private static KbResult empty() {
            return new KbResult("", Map.of());
        }
    }

    private record ToolResult(String toolId, CallToolResult result) {
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks) {
    }
}
