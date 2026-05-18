package com.personalblog.ragbackend.mcp.executor;

import com.personalblog.ragbackend.mcp.tools.RagMcpTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChunkPlainTextMcpExecutor {

    private static final String TOOL_ID = "chunkPlainText";

    private final RagMcpTools ragMcpTools;

    public ChunkPlainTextMcpExecutor(RagMcpTools ragMcpTools) {
        this.ragMcpTools = ragMcpTools;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification chunkPlainTextToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", Map.of("type", "string", "description", "需要切块的原始文本内容"));
        JsonSchema inputSchema = new JsonSchema("object", properties, List.of("content"), null, null, null);
        return Tool.builder()
                .name(TOOL_ID)
                .description("按知识库切块规则对纯文本进行切块，适合在入库前预览 chunk 效果")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String result = ragMcpTools.chunkPlainText(stringArg(args, "content"));
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

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
