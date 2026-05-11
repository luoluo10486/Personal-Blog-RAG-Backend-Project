package com.personalblog.ragbackend.knowledge.service.rag.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeQueryRewriteResult;
import com.personalblog.ragbackend.knowledge.service.rag.intent.GuidanceDecision;
import com.personalblog.ragbackend.knowledge.service.rag.intent.IntentGroup;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.service.rag.intent.RagGuidanceService;
import com.personalblog.ragbackend.knowledge.service.rag.intent.RagIntentNode;
import com.personalblog.ragbackend.knowledge.service.rag.intent.RagIntentResolver;
import com.personalblog.ragbackend.knowledge.service.rag.intent.SubQuestionIntent;
import com.personalblog.ragbackend.knowledge.service.rewrite.QueryTermRewriteService;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RagQueryPipeline {
    private final KnowledgeProperties knowledgeProperties;
    private final QueryTermRewriteService queryTermRewriteService;
    private final RagIntentResolver ragIntentResolver;
    private final RagGuidanceService ragGuidanceService;
    private final ObjectProvider<LLMService> llmServiceProvider;

    public RagQueryPipeline(KnowledgeProperties knowledgeProperties,
                            QueryTermRewriteService queryTermRewriteService,
                            RagIntentResolver ragIntentResolver,
                            RagGuidanceService ragGuidanceService,
                            ObjectProvider<LLMService> llmServiceProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.queryTermRewriteService = queryTermRewriteService;
        this.ragIntentResolver = ragIntentResolver;
        this.ragGuidanceService = ragGuidanceService;
        this.llmServiceProvider = llmServiceProvider;
    }

    @RagTraceNode(name = "rag-query-pipeline", type = "PIPELINE")
    public RagQueryPlan prepare(String baseCode, String question, List<ChatMessage> memory) {
        List<String> steps = new ArrayList<>();
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        steps.add("normalize-base");

        KnowledgeQueryRewriteResult rewriteResult = queryTermRewriteService.rewrite(question, normalizedBaseCode, memory);
        steps.add("rewrite:" + rewriteResult.appliedMappings().size());

        List<SubQuestionIntent> subIntents = ragIntentResolver.resolve(
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions()
        );
        steps.add("intent:" + subIntents.size());

        GuidanceDecision guidanceDecision = ragGuidanceService.detectAmbiguity(rewriteResult.rewrittenQuestion(), subIntents);
        if (guidanceDecision.prompt()) {
            steps.add("guidance:prompt");
            return new RagQueryPlan(
                    rewriteResult.originalQuestion(),
                    rewriteResult.rewrittenQuestion(),
                    normalizedBaseCode,
                    knowledgeProperties.getSearch().getTopK(),
                    subIntents,
                    ragIntentResolver.mergeIntentGroup(subIntents),
                    guidanceDecision,
                    guidanceDecision.promptText(),
                    steps
            );
        }

        IntentGroup intentGroup = ragIntentResolver.mergeIntentGroup(subIntents);
        boolean allSystemOnly = subIntents.stream()
                .allMatch(subQuestionIntent -> ragIntentResolver.isSystemOnly(subQuestionIntent.nodeScores()));
        if (allSystemOnly) {
            String directAnswer = executeSystemOnly(rewriteResult.rewrittenQuestion(), memory, intentGroup);
            steps.add("system-only");
            return new RagQueryPlan(
                    rewriteResult.originalQuestion(),
                    rewriteResult.rewrittenQuestion(),
                    normalizedBaseCode,
                    knowledgeProperties.getSearch().getTopK(),
                    subIntents,
                    intentGroup,
                    guidanceDecision,
                    directAnswer,
                    steps
            );
        }

        String selectedBaseCode = selectBaseCode(normalizedBaseCode, intentGroup);
        int topK = selectTopK(intentGroup);
        steps.add("route:" + selectedBaseCode + "#" + topK);
        return new RagQueryPlan(
                rewriteResult.originalQuestion(),
                rewriteResult.rewrittenQuestion(),
                selectedBaseCode,
                topK,
                subIntents,
                intentGroup,
                guidanceDecision,
                null,
                steps
        );
    }

    private String executeSystemOnly(String rewrittenQuestion, List<ChatMessage> memory, IntentGroup intentGroup) {
        LLMService llmService = llmServiceProvider.getIfAvailable();
        String systemPrompt = resolveSystemPrompt(intentGroup);
        if (StrUtil.isBlank(systemPrompt)) {
            return rewrittenQuestion;
        }
        if (llmService == null) {
            return systemPrompt;
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(memory)) {
            messages.addAll(memory);
        }
        messages.add(ChatMessage.user(rewrittenQuestion));
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(0.3D)
                .topP(0.8D)
                .thinking(false)
                .build();
        try {
            return llmService.chat(request);
        } catch (RuntimeException ignored) {
            return systemPrompt;
        }
    }

    private String resolveSystemPrompt(IntentGroup intentGroup) {
        if (intentGroup == null || CollUtil.isEmpty(intentGroup.kbIntents())) {
            return "";
        }
        return intentGroup.kbIntents().stream()
                .map(NodeScore::node)
                .filter(node -> node != null && node.isSystem())
                .map(node -> {
                    if (StrUtil.isNotBlank(node.promptTemplate)) {
                        return node.promptTemplate;
                    }
                    if (StrUtil.isNotBlank(node.promptSnippet)) {
                        return node.promptSnippet;
                    }
                    return node.fullPath;
                })
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("");
    }

    private String selectBaseCode(String fallbackBaseCode, IntentGroup intentGroup) {
        if (intentGroup == null || CollUtil.isEmpty(intentGroup.kbIntents())) {
            return fallbackBaseCode;
        }
        return intentGroup.kbIntents().stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .map(NodeScore::node)
                .filter(node -> node != null)
                .map(node -> firstNotBlank(node.collectionName, node.intentCode, node.name))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(fallbackBaseCode);
    }

    private int selectTopK(IntentGroup intentGroup) {
        if (intentGroup == null || CollUtil.isEmpty(intentGroup.kbIntents())) {
            return knowledgeProperties.getSearch().getTopK();
        }
        return intentGroup.kbIntents().stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .map(NodeScore::node)
                .filter(node -> node != null && node.topK != null && node.topK > 0)
                .mapToInt(node -> node.topK)
                .findFirst()
                .orElse(knowledgeProperties.getSearch().getTopK());
    }

    private String normalizeBaseCode(String baseCode) {
        if (StrUtil.isBlank(baseCode)) {
            return knowledgeProperties.getDefaultBaseCode();
        }
        return baseCode.trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
