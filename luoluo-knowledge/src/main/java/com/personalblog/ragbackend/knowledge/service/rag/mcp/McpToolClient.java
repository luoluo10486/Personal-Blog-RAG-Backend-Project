package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.config.RagMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class McpToolClient {
    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);

    private final RagMcpProperties ragMcpProperties;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestId = new AtomicLong(1);
    private final HttpClient httpClient;
    private volatile boolean initialized;

    public McpToolClient(RagMcpProperties ragMcpProperties, ObjectMapper objectMapper) {
        this.ragMcpProperties = ragMcpProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ragMcpProperties.getConnectTimeoutMs()))
                .build();
    }

    public List<McpToolDescriptor> listTools() {
        if (!ragMcpProperties.isEnabled()) {
            return List.of();
        }
        tryInitialize();
        JsonNode result = sendRequest("tools/list", Map.of());
        if (result == null || !result.has("tools") || !result.get("tools").isArray()) {
            return List.of();
        }
        List<McpToolDescriptor> tools = new ArrayList<>();
        for (JsonNode toolNode : result.get("tools")) {
            String toolId = textValue(toolNode, "name");
            if (StrUtil.isBlank(toolId)) {
                continue;
            }
            Map<String, McpToolParameter> parameters = new LinkedHashMap<>();
            JsonNode inputSchema = toolNode.path("inputSchema");
            JsonNode requiredNode = inputSchema.path("required");
            JsonNode propertiesNode = inputSchema.path("properties");
            if (propertiesNode.isObject()) {
                propertiesNode.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode property = entry.getValue();
                    parameters.put(name, new McpToolParameter(
                            textValue(property, "type"),
                            textValue(property, "description"),
                            contains(requiredNode, name),
                            property.has("default") && !property.get("default").isNull()
                                    ? objectMapper.convertValue(property.get("default"), Object.class)
                                    : null,
                            toStringList(property.path("enum"))
                    ));
                });
            }
            tools.add(new McpToolDescriptor(toolId, textValue(toolNode, "description"), parameters));
        }
        return tools;
    }

    public McpToolCallResult callTool(String toolId,
                                      Map<String, Object> arguments,
                                      String userId,
                                      String conversationId,
                                      String question) {
        if (!ragMcpProperties.isEnabled() || StrUtil.isBlank(toolId)) {
            return new McpToolCallResult(toolId, false, "", "MCP disabled or toolId missing", arguments == null ? Map.of() : arguments);
        }
        tryInitialize();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolId.trim());
        params.put("arguments", arguments == null ? Map.of() : arguments);
        if (StrUtil.isNotBlank(userId)) {
            params.put("userId", userId.trim());
        }
        if (StrUtil.isNotBlank(conversationId)) {
            params.put("conversationId", conversationId.trim());
        }
        if (StrUtil.isNotBlank(question)) {
            params.put("question", question);
        }

        JsonNode result = sendRequest("tools/call", params);
        if (result == null) {
            return new McpToolCallResult(toolId, false, "", "Remote MCP call returned empty result", params);
        }
        boolean isError = result.path("isError").asBoolean(false);
        String text = extractTextContent(result.path("content"));
        return new McpToolCallResult(
                toolId,
                !isError,
                text,
                isError ? StrUtil.blankToDefault(text, "Unknown MCP error") : "",
                arguments == null ? Map.of() : arguments
        );
    }

    private void tryInitialize() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            JsonNode result = sendRequest("initialize", Map.of(
                    "protocolVersion", "2026-02-28",
                    "clientInfo", Map.of(
                            "name", "luoluo-knowledge",
                            "version", "0.0.1"
                    )
            ));
            initialized = result != null;
            if (initialized) {
                sendNotification("notifications/initialized", Map.of());
            }
        }
    }

    private void sendNotification(String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            if (params != null && !params.isEmpty()) {
                request.put("params", params);
            }
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint()))
                    .timeout(Duration.ofMillis(ragMcpProperties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            log.debug("Ignore MCP notification failure, method={}", method, exception);
        }
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId.getAndIncrement());
            request.put("method", method);
            request.put("params", params == null ? Map.of() : params);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint()))
                    .timeout(Duration.ofMillis(ragMcpProperties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("MCP request failed, method={}, status={}", method, response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                log.warn("MCP request error, method={}, code={}, message={}",
                        method,
                        errorNode.path("code").asText(""),
                        errorNode.path("message").asText(""));
                return null;
            }
            return root.path("result");
        } catch (Exception exception) {
            log.warn("MCP request exception, method={}", method, exception);
            return null;
        }
    }

    private String resolveEndpoint() {
        String serverUrl = ragMcpProperties.getServerUrl().trim();
        return serverUrl.endsWith("/mcp") ? serverUrl : serverUrl + "/mcp";
    }

    private String extractTextContent(JsonNode contentNode) {
        if (contentNode == null || !contentNode.isArray()) {
            return "";
        }
        List<String> textSegments = new ArrayList<>();
        for (JsonNode item : contentNode) {
            String text = textValue(item, "text");
            if (StrUtil.isNotBlank(text)) {
                textSegments.add(text);
            }
        }
        return String.join("\n", textSegments);
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isNull()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private boolean contains(JsonNode node, String candidate) {
        if (node == null || !node.isArray()) {
            return false;
        }
        for (JsonNode item : node) {
            if (candidate.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
