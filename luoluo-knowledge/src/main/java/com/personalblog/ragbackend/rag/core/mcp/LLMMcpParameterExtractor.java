package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.util.LLMResponseCleaner;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMMcpParameterExtractor implements McpParameterExtractor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个 MCP 参数提取器。请根据用户问题和工具定义，提取出工具调用所需的 JSON 参数。
            只返回 JSON 对象，不要输出多余解释。
            """;

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> extractParameters(String userQuestion, MCPTool tool) {
        return extractParameters(userQuestion, tool, null);
    }

    @Override
    public Map<String, Object> extractParameters(String userQuestion, MCPTool tool, String customPromptTemplate) {
        if (tool == null || CollUtil.isEmpty(tool.getParameters())) {
            return Collections.emptyMap();
        }

        List<ChatMessage> messages = List.of(
                ChatMessage.system(StrUtil.blankToDefault(customPromptTemplate, DEFAULT_SYSTEM_PROMPT)),
                ChatMessage.user(buildUserPrompt(userQuestion, tool))
        );

        try {
            String raw = llmService.chat(ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.1D)
                    .topP(0.3D)
                    .thinking(false)
                    .build());
            return fillDefaults(parseJsonResponse(raw, tool), tool);
        } catch (Exception exception) {
            log.warn("MCP parameter extraction failed, toolId={}", tool.getToolId(), exception);
            return fillDefaults(new HashMap<>(), tool);
        }
    }

    private String buildUserPrompt(String userQuestion, MCPTool tool) {
        return "工具定义:\n" + buildToolDefinition(tool) + "\n\n用户问题:\n" + StrUtil.blankToDefault(userQuestion, "");
    }

    private Map<String, Object> parseJsonResponse(String raw, MCPTool tool) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return new HashMap<>();
        }
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<LinkedHashMap<String, Object>>() {});
        Map<String, Object> result = new HashMap<>();
        for (String key : tool.getParameters().keySet()) {
            if (parsed.containsKey(key) && parsed.get(key) != null) {
                result.put(key, parsed.get(key));
            }
        }
        return result;
    }

    private Map<String, Object> fillDefaults(Map<String, Object> params, MCPTool tool) {
        Map<String, Object> targetParams = params == null ? new HashMap<>() : params;
        tool.getParameters().forEach((name, def) -> {
            if (!targetParams.containsKey(name) && def != null && def.getDefaultValue() != null) {
                targetParams.put(name, def.getDefaultValue());
            }
        });
        return targetParams;
    }

    private String buildToolDefinition(MCPTool tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("toolId: ").append(tool.getToolId()).append('\n');
        sb.append("description: ").append(StrUtil.blankToDefault(tool.getDescription(), "")).append('\n');
        sb.append("parameters: ").append(tool.getParameters());
        return sb.toString();
    }
}
