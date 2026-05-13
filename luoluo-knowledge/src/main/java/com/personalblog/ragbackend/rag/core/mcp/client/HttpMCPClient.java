package com.personalblog.ragbackend.rag.core.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.rag.core.mcp.MCPTool;
import com.personalblog.ragbackend.rag.core.mcp.MCPToolDescriptor;
import com.personalblog.ragbackend.rag.core.mcp.MCPToolParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class HttpMCPClient implements MCPClient {

    private final HttpClient httpClient;
    private final String serverUrl;
    private final AtomicLong requestId = new AtomicLong(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean initialize() {
        JsonNode result = sendRequest("initialize", Map.of(
                "protocolVersion", "2026-02-28",
                "clientInfo", Map.of("name", "luoluo-knowledge", "version", "0.0.1")
        ));
        if (result == null) {
            return false;
        }
        sendInitializedNotification();
        return true;
    }

    @Override
    public List<MCPTool> listTools() {
        JsonNode result = sendRequest("tools/list", Map.of());
        List<MCPTool> tools = new ArrayList<>();
        if (result == null || !result.has("tools") || !result.get("tools").isArray()) {
            return tools;
        }
        for (JsonNode toolNode : result.get("tools")) {
            tools.add(convertToMcpTool(toolNode));
        }
        return tools;
    }

    @Override
    public String callTool(String toolName, Map<String, Object> arguments) {
        JsonNode result = sendRequest("tools/call", Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments));
        if (result == null) {
            return null;
        }
        if (result.path("isError").asBoolean(false)) {
            return null;
        }
        return extractTextContent(result);
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId.getAndIncrement());
            request.put("method", method);
            request.put("params", params);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveMcpEndpointUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode rpcResponse = objectMapper.readTree(response.body());
            if (rpcResponse.has("error") && !rpcResponse.get("error").isNull()) {
                return null;
            }
            return rpcResponse.path("result");
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private void sendInitializedNotification() {
        try {
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/initialized");
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveMcpEndpointUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(notification)))
                    .build();
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
        }
    }

    private String resolveMcpEndpointUrl() {
        return serverUrl.endsWith("/mcp") ? serverUrl : serverUrl + "/mcp";
    }

    private String extractTextContent(JsonNode result) {
        if (result == null || !result.has("content") || !result.get("content").isArray()) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (JsonNode item : result.get("content")) {
            if (item.has("text") && !item.get("text").isNull()) {
                segments.add(item.get("text").asText());
            }
        }
        return segments.isEmpty() ? null : String.join("\n", segments);
    }

    private MCPTool convertToMcpTool(JsonNode toolNode) {
        String name = textValue(toolNode, "name");
        String description = textValue(toolNode, "description");
        Map<String, MCPTool.ParameterDef> parameters = new HashMap<>();
        List<String> requiredList = new ArrayList<>();
        if (toolNode.has("inputSchema") && toolNode.get("inputSchema").has("required")) {
            for (JsonNode required : toolNode.get("inputSchema").get("required")) {
                requiredList.add(required.asText());
            }
        }
        if (toolNode.has("inputSchema") && toolNode.get("inputSchema").has("properties")) {
            JsonNode properties = toolNode.get("inputSchema").get("properties");
            properties.fields().forEachRemaining(entry -> {
                JsonNode prop = entry.getValue();
                MCPTool.ParameterDef def = MCPTool.ParameterDef.builder()
                        .type(textValue(prop, "type"))
                        .description(textValue(prop, "description"))
                        .required(requiredList.contains(entry.getKey()))
                        .build();
                if (prop.has("enum") && prop.get("enum").isArray()) {
                    List<String> enumValues = new ArrayList<>();
                    for (JsonNode enumValue : prop.get("enum")) {
                        enumValues.add(enumValue.asText());
                    }
                    def.setEnumValues(enumValues);
                }
                parameters.put(entry.getKey(), def);
            });
        }
        return MCPTool.builder().toolId(name).description(description).parameters(parameters).mcpServerUrl(serverUrl).build();
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }
}
