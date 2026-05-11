package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.config.RagMcpProperties;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpParameterExtractor {
    private static final String DEFAULT_SYSTEM_PROMPT_PATH = "prompt/mcp-parameter-extract.st";

    private final RagMcpProperties ragMcpProperties;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;

    public McpParameterExtractor(RagMcpProperties ragMcpProperties,
                                 ObjectProvider<LLMService> llmServiceProvider,
                                 ObjectMapper objectMapper,
                                 PromptTemplateLoader promptTemplateLoader) {
        this.ragMcpProperties = ragMcpProperties;
        this.llmServiceProvider = llmServiceProvider;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public Map<String, Object> extract(String question,
                                       McpToolDescriptor tool,
                                       String customPromptTemplate,
                                       String baseCode,
                                       Integer topK) {
        Map<String, Object> fallback = buildFallbackParameters(question, tool, baseCode, topK);
        if (!ragMcpProperties.isParameterExtractionEnabled()) {
            return fallback;
        }
        if (tool == null || tool.parameters() == null || tool.parameters().isEmpty()) {
            return fallback;
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallback;
        }

        try {
            String systemPrompt = StrUtil.isNotBlank(customPromptTemplate)
                    ? customPromptTemplate
                    : promptTemplateLoader.load(DEFAULT_SYSTEM_PROMPT_PATH);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(systemPrompt),
                            ChatMessage.user(buildToolDefinition(tool)),
                            ChatMessage.user("Question:\n" + StrUtil.blankToDefault(question, ""))
                    ))
                    .temperature(0.1D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request);
            Map<String, Object> parsed = parseJson(raw, tool);
            if (parsed.isEmpty()) {
                return fallback;
            }
            fillDefaults(parsed, tool);
            mergeMissing(parsed, fallback);
            return parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> buildFallbackParameters(String question,
                                                        McpToolDescriptor tool,
                                                        String baseCode,
                                                        Integer topK) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (tool != null && tool.parameters() != null) {
            fillDefaults(values, tool);
            if (tool.parameters().containsKey("query") && StrUtil.isNotBlank(question)) {
                values.putIfAbsent("query", question.trim());
            }
            if (tool.parameters().containsKey("question") && StrUtil.isNotBlank(question)) {
                values.putIfAbsent("question", question.trim());
            }
            if (tool.parameters().containsKey("content") && StrUtil.isNotBlank(question)) {
                values.putIfAbsent("content", question.trim());
            }
            if (tool.parameters().containsKey("baseCode") && StrUtil.isNotBlank(baseCode)) {
                values.putIfAbsent("baseCode", baseCode.trim());
            }
            if (tool.parameters().containsKey("topK") && topK != null && topK > 0) {
                values.putIfAbsent("topK", topK);
            }
        }
        return values;
    }

    private String buildToolDefinition(McpToolDescriptor tool) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tool ID: ").append(tool.toolId()).append("\n");
        builder.append("Description: ").append(StrUtil.blankToDefault(tool.description(), "")).append("\n");
        builder.append("Parameters:\n");
        tool.parameters().forEach((name, parameter) -> {
            builder.append("- ").append(name)
                    .append(" type=").append(StrUtil.blankToDefault(parameter.type(), "string"))
                    .append(" required=").append(parameter.required())
                    .append(" description=").append(StrUtil.blankToDefault(parameter.description(), ""));
            if (parameter.defaultValue() != null) {
                builder.append(" default=").append(parameter.defaultValue());
            }
            if (parameter.enumValues() != null && !parameter.enumValues().isEmpty()) {
                builder.append(" enum=").append(parameter.enumValues());
            }
            builder.append("\n");
        });
        return builder.toString();
    }

    private Map<String, Object> parseJson(String raw, McpToolDescriptor tool) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return Map.of();
        }
        JsonNode root = objectMapper.readTree(stripCodeFence(raw));
        if (!root.isObject()) {
            return Map.of();
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (String key : tool.parameters().keySet()) {
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            parsed.put(key, objectMapper.convertValue(value, Object.class));
        }
        return parsed;
    }

    private void fillDefaults(Map<String, Object> values, McpToolDescriptor tool) {
        tool.parameters().forEach((name, parameter) -> {
            if (!values.containsKey(name) && parameter.defaultValue() != null) {
                values.put(name, parameter.defaultValue());
            }
        });
    }

    private void mergeMissing(Map<String, Object> target, Map<String, Object> fallback) {
        fallback.forEach(target::putIfAbsent);
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }
}
