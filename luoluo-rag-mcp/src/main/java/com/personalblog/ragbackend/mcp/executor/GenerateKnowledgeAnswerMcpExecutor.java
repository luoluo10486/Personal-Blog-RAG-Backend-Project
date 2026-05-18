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
public class GenerateKnowledgeAnswerMcpExecutor {

    private static final String TOOL_ID = "generateKnowledgeAnswer";

    private final RagMcpTools ragMcpTools;

    public GenerateKnowledgeAnswerMcpExecutor(RagMcpTools ragMcpTools) {
        this.ragMcpTools = ragMcpTools;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification generateKnowledgeAnswerToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "string", "description", "用户问题，例如：订单发货后多久能看到物流"));
        properties.put("topK", Map.of("type", "integer", "description", "检索并参与生成的候选片段数，范围 1 到 20，默认 5"));
        properties.put("baseCode", Map.of("type", "string", "description", "可选知识库编码，留空时使用默认知识库"));

        JsonSchema inputSchema = new JsonSchema("object", properties, List.of("query"), null, null, null);
        return Tool.builder()
                .name(TOOL_ID)
                .description("基于检索到的知识片段生成答案，并返回引用信息")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String result = ragMcpTools.generateKnowledgeAnswer(
                    stringArg(args, "query"),
                    intArg(args, "topK"),
                    stringArg(args, "baseCode")
            );
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

    private Integer intArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
