package com.personalblog.ragbackend.rag.core.guidance;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.util.LLMResponseCleaner;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmbiguityLLMChecker {
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public boolean checkAmbiguity(String question, List<NodeScore> ranked) {
        String candidatesText = buildCandidatesText(ranked);
        String prompt = promptTemplateLoader.render(
                RAGConstant.GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH,
                Map.of(
                        "question", StrUtil.blankToDefault(question, ""),
                        "candidates", candidatesText
                )
        );

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();

        try {
            String raw = llmService.chat(request);
            String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isObject()) {
                log.warn("歧义确认 LLM 返回非 JSON 对象: {}", raw);
                return true;
            }
            JsonNode ambiguousNode = root.get("ambiguous");
            if (ambiguousNode == null || ambiguousNode.isNull()) {
                log.warn("歧义确认 LLM 缺少 ambiguous 字段: {}", raw);
                return true;
            }
            boolean ambiguous = ambiguousNode.asBoolean();
            String reason = root.hasNonNull("reason") ? root.get("reason").asText() : "";
            log.info("歧义确认结果: ambiguous={}, reason={}, question={}", ambiguous, reason, question);
            return ambiguous;
        } catch (Exception exception) {
            log.warn("歧义确认 LLM 调用失败，默认认为存在歧义: question={}", question, exception);
            return true;
        }
    }

    private String buildCandidatesText(List<NodeScore> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return "";
        }
        return ranked.stream()
                .map(candidate -> {
                    if (candidate == null || candidate.node() == null) {
                        return "";
                    }
                    var node = candidate.node();
                    String path = StrUtil.blankToDefault(node.getFullPath(), node.getName());
                    return String.format("- 节点ID: %s, 名称: %s, 路径: %s, 分数: %.2f",
                            node.getId(),
                            StrUtil.blankToDefault(node.getName(), ""),
                            StrUtil.blankToDefault(path, ""),
                            candidate.score());
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }
}
