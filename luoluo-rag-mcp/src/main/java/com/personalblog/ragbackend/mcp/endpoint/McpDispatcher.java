package com.personalblog.ragbackend.mcp.endpoint;

import com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog;
import com.personalblog.ragbackend.mcp.core.McpToolDefinition;
import com.personalblog.ragbackend.mcp.core.McpToolExecutor;
import com.personalblog.ragbackend.mcp.core.McpToolRegistry;
import com.personalblog.ragbackend.mcp.core.McpToolRequest;
import com.personalblog.ragbackend.mcp.core.McpToolResponse;
import com.personalblog.ragbackend.mcp.protocol.JsonRpcError;
import com.personalblog.ragbackend.mcp.protocol.JsonRpcRequest;
import com.personalblog.ragbackend.mcp.protocol.JsonRpcResponse;
import com.personalblog.ragbackend.mcp.protocol.McpToolSchema;
import com.personalblog.ragbackend.mcp.prompts.EnterprisePrompts;
import com.personalblog.ragbackend.mcp.resources.EnterpriseResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class McpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcher.class);

    private final McpToolRegistry toolRegistry;
    private final McpCapabilityCatalog capabilityCatalog;
    private final EnterpriseResources enterpriseResources;
    private final EnterprisePrompts enterprisePrompts;

    public McpDispatcher(McpToolRegistry toolRegistry,
                         McpCapabilityCatalog capabilityCatalog,
                         EnterpriseResources enterpriseResources,
                         EnterprisePrompts enterprisePrompts) {
        this.toolRegistry = toolRegistry;
        this.capabilityCatalog = capabilityCatalog;
        this.enterpriseResources = enterpriseResources;
        this.enterprisePrompts = enterprisePrompts;
    }

    public JsonRpcResponse dispatch(JsonRpcRequest request) {
        if (request == null) {
            return JsonRpcResponse.error(null, JsonRpcError.INVALID_REQUEST, "Request body is required");
        }
        String method = request.getMethod();
        Object id = request.getId();

        if (id == null) {
            log.debug("MCP notification received: {}", method);
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id);
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, request.getParams());
                case "resources/list" -> handleResourcesList(id);
                case "resources/read" -> handleResourcesRead(id, request.getParams());
                case "prompts/list" -> handlePromptsList(id);
                case "prompts/get" -> handlePromptsGet(id, request.getParams());
                default -> JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Unknown method: " + method);
            };
        } catch (Exception exception) {
            log.error("MCP dispatch failed, method={}", method, exception);
            return JsonRpcResponse.error(id, JsonRpcError.INTERNAL_ERROR, exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        }
    }

    private JsonRpcResponse handleInitialize(Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2026-02-28");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("listChanged", false));
        capabilities.put("prompts", Map.of("listChanged", false));
        result.put("capabilities", capabilities);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "luoluo-rag-mcp");
        serverInfo.put("version", "0.0.1");
        result.put("serverInfo", serverInfo);

        return JsonRpcResponse.success(id, result);
    }

    private JsonRpcResponse handleToolsList(Object id) {
        List<McpToolSchema> schemas = toolRegistry.listAllTools().stream()
                .map(this::toSchema)
                .toList();
        return JsonRpcResponse.success(id, Map.of("tools", schemas));
    }

    private JsonRpcResponse handleToolsCall(Object id, Map<String, Object> params) {
        if (params == null || params.get("name") == null) {
            return JsonRpcResponse.error(id, JsonRpcError.INVALID_PARAMS, "Missing 'name' in params");
        }

        String toolName = String.valueOf(params.get("name"));
        Optional<McpToolExecutor> executorOpt = toolRegistry.getExecutor(toolName);
        if (executorOpt.isEmpty()) {
            return JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Tool not found: " + toolName);
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        Object rawArguments = params.get("arguments");
        if (rawArguments instanceof Map<?, ?> argumentMap) {
            for (Map.Entry<?, ?> entry : argumentMap.entrySet()) {
                if (entry.getKey() != null) {
                    arguments.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        McpToolRequest toolRequest = new McpToolRequest();
        toolRequest.setToolId(toolName);
        toolRequest.setParameters(arguments);
        if (params.get("userId") != null) {
            toolRequest.setUserId(String.valueOf(params.get("userId")));
        }
        if (params.get("conversationId") != null) {
            toolRequest.setConversationId(String.valueOf(params.get("conversationId")));
        }
        if (params.get("question") != null) {
            toolRequest.setUserQuestion(String.valueOf(params.get("question")));
        }

        try {
            McpToolResponse toolResponse = executorOpt.get().execute(toolRequest);
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", toolResponse.isSuccess()
                    ? (toolResponse.getTextResult() != null ? toolResponse.getTextResult() : "")
                    : (toolResponse.getErrorMessage() != null ? toolResponse.getErrorMessage() : ""));
            content.add(textContent);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("isError", !toolResponse.isSuccess());
            return JsonRpcResponse.success(id, result);
        } catch (Exception exception) {
            log.error("Tool execution failed: {}", toolName, exception);
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "工具调用异常: " + exception.getMessage());
            content.add(textContent);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("isError", true);
            return JsonRpcResponse.success(id, result);
        }
    }

    private JsonRpcResponse handleResourcesList(Object id) {
        List<Map<String, Object>> resources = capabilityCatalog.resources().stream()
                .map(resource -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("uri", resource.uri());
                    item.put("name", resource.name());
                    item.put("description", resource.description());
                    item.put("mimeType", resource.mimeType());
                    return item;
                })
                .toList();
        return JsonRpcResponse.success(id, Map.of("resources", resources));
    }

    private JsonRpcResponse handleResourcesRead(Object id, Map<String, Object> params) {
        if (params == null || params.get("uri") == null) {
            return JsonRpcResponse.error(id, JsonRpcError.INVALID_PARAMS, "Missing 'uri' in params");
        }
        String uri = String.valueOf(params.get("uri"));
        if (enterpriseResources.isReturnPolicy(uri)) {
            return JsonRpcResponse.success(id, Map.of(
                    "contents", List.of(textResource(uri, "text/plain", enterpriseResources.getReturnPolicy()))
            ));
        }
        if (enterpriseResources.isOrderDetail(uri)) {
            return JsonRpcResponse.success(id, Map.of(
                    "contents", List.of(textResource(uri, "application/json", enterpriseResources.getOrderDetail(uri)))
            ));
        }
        return JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Unsupported resource URI: " + uri);
    }

    private JsonRpcResponse handlePromptsList(Object id) {
        List<Map<String, Object>> prompts = capabilityCatalog.prompts().stream()
                .map(prompt -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", prompt.name());
                    item.put("description", prompt.description());
                    item.put("arguments", prompt.arguments().stream().map(argument -> {
                        Map<String, Object> arg = new LinkedHashMap<>();
                        arg.put("name", argument.name());
                        arg.put("description", argument.description());
                        arg.put("required", argument.required());
                        return arg;
                    }).toList());
                    return item;
                })
                .toList();
        return JsonRpcResponse.success(id, Map.of("prompts", prompts));
    }

    private JsonRpcResponse handlePromptsGet(Object id, Map<String, Object> params) {
        if (params == null || params.get("name") == null) {
            return JsonRpcResponse.error(id, JsonRpcError.INVALID_PARAMS, "Missing 'name' in params");
        }
        String name = String.valueOf(params.get("name"));
        Map<String, Object> arguments = new LinkedHashMap<>();
        Object rawArguments = params.get("arguments");
        if (rawArguments instanceof Map<?, ?> argumentMap) {
            for (Map.Entry<?, ?> entry : argumentMap.entrySet()) {
                if (entry.getKey() != null) {
                    arguments.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        if ("knowledge-qa".equals(name)) {
            EnterprisePrompts.PromptResult result = enterprisePrompts.knowledgeQaPrompt(arguments);
            return JsonRpcResponse.success(id, Map.of("description", result.description(), "messages", promptMessages(result.messages())));
        }
        if ("doc-summary".equals(name)) {
            EnterprisePrompts.PromptResult result = enterprisePrompts.docSummaryPrompt(arguments);
            return JsonRpcResponse.success(id, Map.of("description", result.description(), "messages", promptMessages(result.messages())));
        }
        return JsonRpcResponse.error(id, JsonRpcError.METHOD_NOT_FOUND, "Unsupported prompt: " + name);
    }

    private List<Map<String, Object>> promptMessages(List<EnterprisePrompts.PromptMessage> messages) {
        return messages.stream()
                .map(message -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", message.role());
                    item.put("content", Map.of("type", "text", "text", message.content()));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> textResource(String uri, String mimeType, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", uri);
        content.put("mimeType", mimeType);
        content.put("text", text);
        return content;
    }

    private McpToolSchema toSchema(McpToolDefinition definition) {
        Map<String, McpToolSchema.PropertyDef> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (definition.getParameters() != null) {
            definition.getParameters().forEach((name, parameter) -> {
                properties.put(name, new McpToolSchema.PropertyDef(
                        parameter.getType(),
                        parameter.getDescription(),
                        parameter.getEnumValues()
                ));
                if (parameter.isRequired()) {
                    required.add(name);
                }
            });
        }

        return new McpToolSchema(
                definition.getToolId(),
                definition.getDescription(),
                new McpToolSchema.InputSchema("object", properties, required.isEmpty() ? null : required)
        );
    }
}
