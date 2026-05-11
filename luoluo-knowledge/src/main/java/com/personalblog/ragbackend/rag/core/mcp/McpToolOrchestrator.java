package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.rag.config.RagMcpProperties;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class McpToolOrchestrator {
    private final RagMcpProperties ragMcpProperties;
    private final McpToolClient mcpToolClient;
    private final McpParameterExtractor mcpParameterExtractor;
    private final McpContextFormatter mcpContextFormatter;

    public McpToolOrchestrator(RagMcpProperties ragMcpProperties,
                               McpToolClient mcpToolClient,
                               McpParameterExtractor mcpParameterExtractor,
                               McpContextFormatter mcpContextFormatter) {
        this.ragMcpProperties = ragMcpProperties;
        this.mcpToolClient = mcpToolClient;
        this.mcpParameterExtractor = mcpParameterExtractor;
        this.mcpContextFormatter = mcpContextFormatter;
    }

    @RagTraceNode(name = "mcp-tool-orchestrate", type = "MCP")
    public String executeAndFormat(List<SubQuestionIntent> subIntents,
                                   String baseCode,
                                   int topK,
                                   String conversationId) {
        if (!ragMcpProperties.isEnabled() || CollUtil.isEmpty(subIntents)) {
            return "";
        }

        Map<String, McpToolDescriptor> toolMap = mcpToolClient.listTools().stream()
                .collect(LinkedHashMap::new, (map, tool) -> map.put(tool.toolId(), tool), LinkedHashMap::putAll);
        if (toolMap.isEmpty()) {
            return "";
        }

        List<CompletableFuture<String>> tasks = subIntents.stream()
                .map(subIntent -> CompletableFuture.supplyAsync(() -> executeSingleSubQuestion(subIntent, toolMap, baseCode, topK, conversationId)))
                .toList();

        return tasks.stream()
                .map(CompletableFuture::join)
                .filter(StrUtil::isNotBlank)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");
    }

    private String executeSingleSubQuestion(SubQuestionIntent subIntent,
                                            Map<String, McpToolDescriptor> toolMap,
                                            String baseCode,
                                            int topK,
                                            String conversationId) {
        List<NodeScore> mcpIntents = subIntent.nodeScores().stream()
                .filter(score -> score.node() != null && score.node().isMcp())
                .toList();
        if (mcpIntents.isEmpty()) {
            return "";
        }

        String userId = StrUtil.blankToDefault(UserContext.getUserId(), "");
        List<McpToolCallResult> results = mcpIntents.stream()
                .map(score -> executeSingleIntent(score, subIntent.subQuestion(), toolMap, baseCode, topK, conversationId, userId))
                .toList();

        String content = mcpContextFormatter.format(results, mcpIntents);
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return "Sub-question: " + StrUtil.blankToDefault(subIntent.subQuestion(), "") + "\n\n" + content;
    }

    private McpToolCallResult executeSingleIntent(NodeScore score,
                                                  String question,
                                                  Map<String, McpToolDescriptor> toolMap,
                                                  String baseCode,
                                                  int topK,
                                                  String conversationId,
                                                  String userId) {
        if (score.node() == null || StrUtil.isBlank(score.node().mcpToolId)) {
            return new McpToolCallResult("", false, "", "Missing MCP tool id", Map.of());
        }

        String toolId = score.node().mcpToolId.trim();
        McpToolDescriptor tool = toolMap.get(toolId);
        if (tool == null) {
            return new McpToolCallResult(toolId, false, "", "MCP tool not found: " + toolId, Map.of());
        }

        int effectiveTopK = score.node().topK != null && score.node().topK > 0 ? score.node().topK : topK;
        Map<String, Object> arguments = mcpParameterExtractor.extract(
                question,
                tool,
                score.node().paramPromptTemplate,
                baseCode,
                effectiveTopK
        );
        return mcpToolClient.callTool(toolId, arguments, userId, conversationId, question);
    }
}
