package com.personalblog.ragbackend.rag.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class McpClientToolExecutor implements McpToolExecutor {
    private final McpSyncClient mcpClient;
    private final Tool toolDefinition;
    private final MCPTool toolView;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpClientToolExecutor(McpSyncClient mcpClient, Tool toolDefinition, String serverUrl) {
        this.mcpClient = mcpClient;
        this.toolDefinition = toolDefinition;
        this.toolView = convertToToolView(toolDefinition, serverUrl);
    }

    @Override
    public MCPTool getToolDefinition() {
        return toolView;
    }

    @Override
    public String getToolId() {
        return toolDefinition.name();
    }

    @Override
    public MCPResponse execute(MCPRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> parameters = request.getParameters() == null ? Map.of() : request.getParameters();
            CallToolResult result = mcpClient.callTool(new CallToolRequest(toolDefinition.name(), parameters));
            String text = extractTextContent(result);
            long costMs = System.currentTimeMillis() - startMs;
            MCPResponse response = MCPResponse.success(request.getToolId(), text);
            response.setCostMs(costMs);
            return response;
        } catch (Exception exception) {
            long costMs = System.currentTimeMillis() - startMs;
            String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            log.warn("remote MCP tool call failed, toolId={}, reason={}", request.getToolId(), reason);
            MCPResponse response = MCPResponse.error(request.getToolId(), "REMOTE_CALL_ERROR", reason);
            response.setCostMs(costMs);
            return response;
        }
    }

    private String extractTextContent(CallToolResult result) {
        if (result == null || result.content() == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        result.content().forEach(content -> {
            if (content instanceof TextContent textContent) {
                if (textContent.text() != null) {
                    segments.add(textContent.text());
                }
            } else if (content != null) {
                segments.add(String.valueOf(content));
            }
        });
        return segments.isEmpty() ? null : String.join("\n", segments);
    }

    private MCPTool convertToToolView(Tool tool, String serverUrl) {
        Map<String, MCPTool.ParameterDef> parameters = new LinkedHashMap<>();
        List<String> requiredList = new ArrayList<>();
        JsonNode inputSchema = objectMapper.valueToTree(tool.inputSchema());
        if (inputSchema.has("required") && inputSchema.get("required").isArray()) {
            inputSchema.get("required").forEach(required -> requiredList.add(required.asText()));
        }
        if (inputSchema.has("properties") && inputSchema.get("properties").isObject()) {
            inputSchema.get("properties").fields().forEachRemaining(entry -> {
                JsonNode prop = entry.getValue();
                MCPTool.ParameterDef def = MCPTool.ParameterDef.builder()
                        .type(textValue(prop, "type"))
                        .description(textValue(prop, "description"))
                        .required(requiredList.contains(entry.getKey()))
                        .build();
                if (prop.has("enum") && prop.get("enum").isArray()) {
                    List<String> enumValues = new ArrayList<>();
                    prop.get("enum").forEach(enumValue -> enumValues.add(enumValue.asText()));
                    def.setEnumValues(enumValues);
                }
                parameters.put(entry.getKey(), def);
            });
        }
        return MCPTool.builder()
                .toolId(tool.name())
                .description(tool.description())
                .parameters(parameters)
                .mcpServerUrl(serverUrl)
                .build();
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }
}
