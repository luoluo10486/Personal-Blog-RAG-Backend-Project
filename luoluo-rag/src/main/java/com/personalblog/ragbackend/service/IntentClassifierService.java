package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagConversationMessage;
import com.personalblog.ragbackend.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 意图分类服务：规则优先，未命中时再调用小模型兜底。
 */
@Service
public class IntentClassifierService {
    private static final Logger log = LoggerFactory.getLogger(IntentClassifierService.class);

    private static final Set<String> CHITCHAT_KEYWORDS = Set.of(
            "你好", "您好", "谢谢", "感谢", "再见", "拜拜",
            "哈哈", "嗯嗯", "好的", "收到", "明白了", "ok", "OK"
    );

    private static final Set<String> CLARIFICATION_KEYWORDS = Set.of(
            "有什么推荐的", "怎么办", "帮我看看", "怎么弄", "咋办"
    );

    private static final Set<String> VALID_INTENTS = Set.of(
            "knowledge", "tool", "chitchat", "clarification"
    );

    private static final String CLASSIFY_PROMPT = """
            你是一个意图分类助手。请根据对话历史和用户的最新消息，判断用户的意图类别。

            意图类别定义：
            1. knowledge - 知识检索：用户在询问产品信息、政策规定、操作指南等通用知识。
            2. tool - 工具调用：用户想查询个人数据、实时信息，或执行某个操作。
            3. chitchat - 闲聊对话：用户在打招呼、感谢、闲聊，不涉及具体业务问题。
            4. clarification - 引导澄清：用户的问题太模糊，缺少关键信息，暂时无法确定意图。

            判断规则：
            - 结合对话历史判断，相同的话在不同上下文中意图可能不同。
            - 如果用户的问题涉及“我的”“查一下”“帮我处理”等个人化表达，通常是工具调用。
            - 如果问题在问通用规则、政策、产品信息，通常是知识检索。
            - 只有在真的无法判断意图时才分类为 clarification。
            - 仅输出 JSON：{"intent":"分类结果","confidence":0.95}
            - 不要输出 JSON 以外的任何内容。

            对话历史：
            %s

            用户最新消息：
            %s
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public IntentClassifierService(HttpClient ragHttpClient, ObjectMapper objectMapper, RagProperties ragProperties) {
        this.httpClient = ragHttpClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    public IntentResult classify(List<RagConversationMessage> history, String query) {
        if (query == null || query.isBlank()) {
            return new IntentResult("clarification", 1.0, "rule");
        }

        IntentResult ruleResult = classifyByRule(query);
        if (ruleResult != null) {
            return ruleResult;
        }

        if (!ragProperties.getIntent().isEnabled()
                || ragProperties.getApiKey() == null
                || ragProperties.getApiKey().isBlank()) {
            return IntentResult.knowledgeFallback("disabled");
        }

        try {
            return classifyByLlm(history, query);
        } catch (Exception exception) {
            log.warn("意图分类降级为 knowledge：query={}, reason={}", query, exception.getMessage());
            return IntentResult.knowledgeFallback("fallback");
        }
    }

    private IntentResult classifyByRule(String query) {
        String normalized = query.trim();
        if (normalized.length() <= 6 && CHITCHAT_KEYWORDS.contains(normalized)) {
            return new IntentResult("chitchat", 0.99, "rule");
        }
        if (CLARIFICATION_KEYWORDS.contains(normalized)) {
            return new IntentResult("clarification", 0.95, "rule");
        }
        return null;
    }

    private IntentResult classifyByLlm(List<RagConversationMessage> history, String query) throws IOException, InterruptedException {
        String prompt = String.format(
                Locale.ROOT,
                CLASSIFY_PROMPT,
                buildHistoryText(history),
                query.trim()
        );

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", ragProperties.getIntent().getModel());
        requestBody.put("temperature", ragProperties.getIntent().getTemperature());
        requestBody.put("max_tokens", ragProperties.getIntent().getMaxTokens());
        requestBody.put("stream", false);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ragProperties.getApiUrl()))
                .timeout(Duration.ofSeconds(ragProperties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + ragProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
        JsonNode resultNode = objectMapper.readTree(stripCodeFence(content));
        String intent = resultNode.path("intent").asText("knowledge");
        double confidence = resultNode.path("confidence").asDouble(0.8);
        if (!VALID_INTENTS.contains(intent)) {
            return IntentResult.knowledgeFallback("invalid");
        }
        return new IntentResult(intent, confidence, "llm");
    }

    private String buildHistoryText(List<RagConversationMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（无历史对话）";
        }

        StringBuilder builder = new StringBuilder();
        for (RagConversationMessage message : history) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.role()) ? "助手" : "用户";
            builder.append(role).append("：").append(message.content().trim()).append("\n");
        }
        return builder.isEmpty() ? "（无历史对话）" : builder.toString().trim();
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    public record IntentResult(String intent, double confidence, String source) {
        public static IntentResult knowledgeFallback(String source) {
            return new IntentResult("knowledge", 0.5, source);
        }
    }
}
