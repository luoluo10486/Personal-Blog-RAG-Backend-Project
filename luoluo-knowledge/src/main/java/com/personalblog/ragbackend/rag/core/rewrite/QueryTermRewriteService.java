package com.personalblog.ragbackend.rag.core.rewrite;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeQueryRewriteResult;
import com.personalblog.ragbackend.rag.dao.mapper.QueryTermMappingMapper;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class QueryTermRewriteService {
    private static final String REWRITE_PROMPT_PATH = "prompt/user-question-rewrite.st";

    private final QueryTermMappingMapper queryTermMappingMapper;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public QueryTermRewriteService(QueryTermMappingMapper queryTermMappingMapper,
                                   ObjectProvider<LLMService> llmServiceProvider,
                                   PromptTemplateLoader promptTemplateLoader,
                                   ObjectMapper objectMapper) {
        this.queryTermMappingMapper = queryTermMappingMapper;
        this.llmServiceProvider = llmServiceProvider;
        this.promptTemplateLoader = promptTemplateLoader;
        this.objectMapper = objectMapper;
    }

    @RagTraceNode(name = "query-rewrite", type = "REWRITE")
    public KnowledgeQueryRewriteResult rewrite(String question, String domain) {
        return rewrite(question, domain, List.of());
    }

    @RagTraceNode(name = "query-rewrite", type = "REWRITE")
    public KnowledgeQueryRewriteResult rewrite(String question, String domain, List<ChatMessage> history) {
        String originalQuestion = question == null ? "" : question.trim();
        if (!StringUtils.hasText(originalQuestion)) {
            return new KnowledgeQueryRewriteResult("", "", List.of(), List.of());
        }

        List<QueryTermMappingEntity> mappings = queryTermMappingMapper.selectList(
                new QueryWrapper<QueryTermMappingEntity>()
                        .eq("enabled", 1)
                        .eq("deleted", 0)
                        .and(wrapper -> {
                            if (StringUtils.hasText(domain)) {
                                wrapper.eq("domain", domain.trim())
                                        .or()
                                        .isNull("domain")
                                        .or()
                                        .eq("domain", "");
                            } else {
                                wrapper.isNull("domain").or().eq("domain", "");
                            }
                        })
                        .orderByAsc("priority")
                        .orderByAsc("id")
        );

        String normalizedQuestion = originalQuestion;
        List<String> appliedMappings = new ArrayList<>();
        for (QueryTermMappingEntity mapping : mappings.stream()
                .sorted(Comparator.comparingInt(value -> value.priority == null ? 100 : value.priority))
                .toList()) {
            String candidate = applyMapping(normalizedQuestion, mapping);
            if (!candidate.equals(normalizedQuestion)) {
                appliedMappings.add(describe(mapping));
                normalizedQuestion = candidate;
            }
        }

        RewriteOutput rewriteOutput = rewriteWithLLM(normalizedQuestion, history);
        String rewrittenQuestion = rewriteOutput.rewrite();
        List<String> subQuestions = rewriteOutput.subQuestions();
        return new KnowledgeQueryRewriteResult(
                originalQuestion,
                StrUtil.blankToDefault(rewrittenQuestion, normalizedQuestion),
                appliedMappings,
                subQuestions
        );
    }

    private RewriteOutput rewriteWithLLM(String normalizedQuestion, List<ChatMessage> history) {
        if (!StringUtils.hasText(normalizedQuestion)) {
            return new RewriteOutput(normalizedQuestion, List.of(normalizedQuestion));
        }
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return new RewriteOutput(normalizedQuestion, List.of(normalizedQuestion));
        }

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(promptTemplateLoader.load(REWRITE_PROMPT_PATH)));
            if (history != null && !history.isEmpty()) {
                List<ChatMessage> recentHistory = history.stream()
                        .filter(msg -> msg.getRole() == ChatMessage.Role.USER || msg.getRole() == ChatMessage.Role.ASSISTANT)
                        .skip(Math.max(0, history.size() - 4))
                        .toList();
                messages.addAll(recentHistory);
            }
            messages.add(ChatMessage.user(normalizedQuestion));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.1D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request);
            return parseRewriteOutput(raw, normalizedQuestion);
        } catch (Exception ignored) {
            return new RewriteOutput(normalizedQuestion, List.of(normalizedQuestion));
        }
    }

    private RewriteOutput parseRewriteOutput(String raw, String fallbackQuestion) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return new RewriteOutput(fallbackQuestion, List.of(fallbackQuestion));
        }
        JsonNode root = objectMapper.readTree(stripCodeFence(raw));
        if (!root.isObject()) {
            return new RewriteOutput(fallbackQuestion, List.of(fallbackQuestion));
        }
        String rewrite = root.path("rewrite").asText("").trim();
        List<String> subQuestions = new ArrayList<>();
        JsonNode subQuestionsNode = root.path("sub_questions");
        if (subQuestionsNode.isArray()) {
            for (JsonNode node : subQuestionsNode) {
                String subQuestion = node == null ? "" : node.asText("").trim();
                if (StringUtils.hasText(subQuestion)) {
                    subQuestions.add(subQuestion);
                }
            }
        }
        String normalizedRewrite = StrUtil.blankToDefault(rewrite, fallbackQuestion);
        if (subQuestions.isEmpty()) {
            subQuestions.add(normalizedRewrite);
        }
        return new RewriteOutput(normalizedRewrite, subQuestions);
    }

    private String applyMapping(String question, QueryTermMappingEntity mapping) {
        if (mapping == null || !StringUtils.hasText(question) || !StringUtils.hasText(mapping.sourceTerm) || !StringUtils.hasText(mapping.targetTerm)) {
            return question;
        }
        String source = mapping.sourceTerm.trim();
        String target = mapping.targetTerm.trim();
        int matchType = mapping.matchType == null ? 1 : mapping.matchType;
        return switch (matchType) {
            case 2 -> replacePrefix(question, source, target);
            case 3 -> replaceRegex(question, source, target);
            case 4 -> replaceWholeWord(question, source, target);
            default -> question.replace(source, target);
        };
    }

    private String replacePrefix(String question, String source, String target) {
        if (question.startsWith(source)) {
            return target + question.substring(source.length());
        }
        return question;
    }

    private String replaceRegex(String question, String source, String target) {
        try {
            return question.replaceAll(source, target);
        } catch (RuntimeException ignored) {
            return question;
        }
    }

    private String replaceWholeWord(String question, String source, String target) {
        String safeSource = Pattern.quote(source);
        String pattern = "(?<![\\p{L}\\p{N}_])" + safeSource + "(?![\\p{L}\\p{N}_])";
        try {
            return question.replaceAll(pattern, target);
        } catch (RuntimeException ignored) {
            return question.replace(source, target);
        }
    }

    private String describe(QueryTermMappingEntity mapping) {
        String domain = StringUtils.hasText(mapping.domain) ? mapping.domain.trim() : "*";
        return String.format(
                Locale.ROOT,
                "%s:%s->%s#%s",
                domain,
                mapping.sourceTerm,
                mapping.targetTerm,
                mapping.matchType == null ? 1 : mapping.matchType
        );
    }

    private String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }

    private record RewriteOutput(String rewrite, List<String> subQuestions) {
    }
}

