package com.personalblog.ragbackend.rag.core.rewrite;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class MultiQuestionRewriteService implements QueryRewriteService {
    private static final String REWRITE_PROMPT_PATH = "prompt/user-question-rewrite.st";

    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public MultiQuestionRewriteService(LLMService llmService,
                                       PromptTemplateLoader promptTemplateLoader,
                                       ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    @RagTraceNode(name = "query-rewrite", type = "REWRITE")
    public String rewrite(String userQuestion) {
        return rewriteAndSplit(userQuestion).rewrittenQuestion();
    }

    @Override
    public RewriteResult rewriteWithSplit(String userQuestion) {
        return rewriteAndSplit(userQuestion);
    }

    @Override
    @RagTraceNode(name = "query-rewrite-and-split", type = "REWRITE")
    public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history) {
        return callLLMRewriteAndSplit(normalize(userQuestion), userQuestion, history);
    }

    private RewriteResult rewriteAndSplit(String userQuestion) {
        return callLLMRewriteAndSplit(normalize(userQuestion), userQuestion, List.of());
    }

    private RewriteResult callLLMRewriteAndSplit(String normalizedQuestion,
                                                 String originalQuestion,
                                                 List<ChatMessage> history) {
        String systemPrompt = promptTemplateLoader.load(REWRITE_PROMPT_PATH);
        ChatRequest req = buildRewriteRequest(systemPrompt, normalizedQuestion, history);
        try {
            String raw = llmService.chat(req);
            RewriteResult parsed = parseRewriteAndSplit(raw);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return new RewriteResult(normalizedQuestion, List.of(normalizedQuestion));
    }

    private ChatRequest buildRewriteRequest(String systemPrompt,
                                            String question,
                                            List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (CollUtil.isNotEmpty(history)) {
            List<ChatMessage> recentHistory = history.stream()
                    .filter(msg -> msg.getRole() == ChatMessage.Role.USER || msg.getRole() == ChatMessage.Role.ASSISTANT)
                    .skip(Math.max(0, history.size() - 4))
                    .toList();
            messages.addAll(recentHistory);
        }
        messages.add(ChatMessage.user(question));
        return ChatRequest.builder()
                .messages(messages)
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();
    }

    private RewriteResult parseRewriteAndSplit(String raw) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(raw));
            if (!root.isObject()) {
                return null;
            }
            String rewrite = root.path("rewrite").asText("").trim();
            List<String> subs = new ArrayList<>();
            JsonNode arr = root.path("sub_questions");
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String text = node == null ? "" : node.asText("").trim();
                    if (StrUtil.isNotBlank(text)) {
                        subs.add(text);
                    }
                }
            }
            if (StrUtil.isBlank(rewrite)) {
                return null;
            }
            if (CollUtil.isEmpty(subs)) {
                subs = List.of(rewrite);
            }
            return new RewriteResult(rewrite, subs);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
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
