package com.personalblog.ragbackend.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.personalblog.ragbackend.rag.constant.RAGConstant.GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH;

@Component
public class AmbiguityLLMChecker {
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public AmbiguityLLMChecker(LLMService llmService,
                               PromptTemplateLoader promptTemplateLoader,
                               ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.objectMapper = objectMapper;
    }

    public boolean checkAmbiguity(String question, List<NodeScore> ranked) {
        String candidatesText = buildCandidatesText(ranked);
        String prompt = promptTemplateLoader.render(
                GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH,
                Map.of(
                        "question", question,
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
            String cleaned = PromptTemplateUtils.cleanupPrompt(raw);
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isObject()) {
                return true;
            }
            JsonNode ambiguous = root.get("ambiguous");
            if (ambiguous != null && ambiguous.isBoolean()) {
                return ambiguous.asBoolean();
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private String buildCandidatesText(List<NodeScore> ranked) {
        return ranked.stream()
                .map(ns -> {
                    IntentNode node = ns == null ? null : ns.node();
                    if (node == null) {
                        return "";
                    }
                    String systemPath = node.getFullPath() != null ? node.getFullPath() : node.getName();
                    return String.format("- 品类ID: %s, 名称: %s, 路径: %s, 分数: %.2f",
                            node.getId(), node.getName(), systemPath, ns.score());
                })
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
