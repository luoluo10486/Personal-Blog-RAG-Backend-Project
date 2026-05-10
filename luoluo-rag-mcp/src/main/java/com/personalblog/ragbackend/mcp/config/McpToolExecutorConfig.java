package com.personalblog.ragbackend.mcp.config;

import com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog;
import com.personalblog.ragbackend.mcp.core.LambdaMcpToolExecutor;
import com.personalblog.ragbackend.mcp.core.McpToolDefinition;
import com.personalblog.ragbackend.mcp.core.McpToolExecutor;
import com.personalblog.ragbackend.mcp.core.McpToolRequest;
import com.personalblog.ragbackend.mcp.core.McpToolResponse;
import com.personalblog.ragbackend.mcp.prompts.EnterprisePrompts;
import com.personalblog.ragbackend.mcp.resources.EnterpriseResources;
import com.personalblog.ragbackend.mcp.tools.RagMcpTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
public class McpToolExecutorConfig {

    @Bean
    public McpToolExecutor getRagStatusMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "getRagStatus", request -> safeTextTool("getRagStatus", ragMcpTools::getRagStatus));
    }

    @Bean
    public McpToolExecutor searchKnowledgeBaseMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "searchKnowledgeBase", request -> safeTextTool("searchKnowledgeBase", () ->
                ragMcpTools.searchKnowledgeBase(
                        request.getStringParameter("query"),
                        toInteger(request.getParameter("topK")),
                        request.getStringParameter("baseCode")
                )));
    }

    @Bean
    public McpToolExecutor generateKnowledgeAnswerMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "generateKnowledgeAnswer", request -> safeTextTool("generateKnowledgeAnswer", () ->
                ragMcpTools.generateKnowledgeAnswer(
                        request.getStringParameter("query"),
                        toInteger(request.getParameter("topK")),
                        request.getStringParameter("baseCode")
                )));
    }

    @Bean
    public McpToolExecutor chunkPlainTextMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "chunkPlainText", request -> safeTextTool("chunkPlainText", () ->
                ragMcpTools.chunkPlainText(request.getStringParameter("content"))));
    }

    @Bean
    public McpToolExecutor previewKnowledgeCitationsMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "previewKnowledgeCitations", request -> safeTextTool("previewKnowledgeCitations", () ->
                ragMcpTools.previewKnowledgeCitations(
                        request.getStringParameter("query"),
                        toInteger(request.getParameter("topK")),
                        request.getStringParameter("baseCode")
                )));
    }

    @Bean
    public McpToolExecutor describeMcpCapabilitiesMcpToolExecutor(RagMcpTools ragMcpTools, McpCapabilityCatalog catalog) {
        return executor(catalog, "describeMcpCapabilities", request -> safeTextTool("describeMcpCapabilities", ragMcpTools::describeMcpCapabilities));
    }

    private McpToolExecutor executor(McpCapabilityCatalog catalog, String toolId, java.util.function.Function<McpToolRequest, McpToolResponse> handler) {
        return new LambdaMcpToolExecutor(toDefinition(catalog, toolId), handler);
    }

    private McpToolDefinition toDefinition(McpCapabilityCatalog catalog, String toolId) {
        McpCapabilityCatalog.ToolMetadata metadata = catalog.tool(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Missing MCP tool metadata: " + toolId));
        Map<String, McpToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();
        metadata.parameters().forEach((name, parameter) -> parameters.put(name, new McpToolDefinition.ParameterDef(
                parameter.description(),
                parameter.type(),
                parameter.required(),
                parameter.defaultValue(),
                parameter.enumValues()
        )));
        return new McpToolDefinition(metadata.toolId(), metadata.description(), parameters, metadata.requireUserId());
    }

    private McpToolResponse safeTextTool(String toolId, Supplier<String> supplier) {
        long start = System.currentTimeMillis();
        try {
            McpToolResponse response = McpToolResponse.success(toolId, supplier.get());
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        } catch (Exception exception) {
            McpToolResponse response = McpToolResponse.error(toolId, "EXECUTION_ERROR", messageOf(exception));
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
