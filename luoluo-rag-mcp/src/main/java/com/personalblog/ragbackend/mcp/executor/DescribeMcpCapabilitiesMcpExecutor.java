package com.personalblog.ragbackend.mcp.executor;

import com.personalblog.ragbackend.mcp.tools.RagMcpTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DescribeMcpCapabilitiesMcpExecutor {

    private static final String TOOL_ID = "describeMcpCapabilities";

    private final RagMcpTools ragMcpTools;

    public DescribeMcpCapabilitiesMcpExecutor(RagMcpTools ragMcpTools) {
        this.ragMcpTools = ragMcpTools;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification describeMcpCapabilitiesToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall());
    }

    private Tool buildTool() {
        JsonSchema inputSchema = new JsonSchema("object", Map.of(), null, null, null, null);
        return Tool.builder()
                .name(TOOL_ID)
                .description("列出当前 MCP 服务提供的 tools 信息")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall() {
        long startMs = System.currentTimeMillis();
        try {
            String result = ragMcpTools.describeMcpCapabilities();
            logSuccess(startMs);
            return successResult(result);
        } catch (Exception exception) {
            logFailure(startMs, exception);
            return errorResult("查询失败: " + messageOf(exception));
        }
    }

    private void logSuccess(long startMs) {
        System.out.println("MCP tool call complete, toolId=" + TOOL_ID + ", elapsed=" + (System.currentTimeMillis() - startMs) + "ms");
    }

    private void logFailure(long startMs, Exception exception) {
        System.err.println("MCP tool call failed, toolId=" + TOOL_ID + ", elapsed=" + (System.currentTimeMillis() - startMs) + "ms, reason=" + messageOf(exception));
    }

    private CallToolResult successResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text == null ? "" : text)))
                .isError(false)
                .build();
    }

    private CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message == null ? "" : message)))
                .isError(true)
                .build();
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
