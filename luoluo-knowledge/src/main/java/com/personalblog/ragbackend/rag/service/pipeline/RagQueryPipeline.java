package com.personalblog.ragbackend.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.core.guidance.GuidanceDecision;
import com.personalblog.ragbackend.rag.core.guidance.IntentGuidanceService;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.IntentResolver;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.rewrite.QueryRewriteService;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagQueryPipeline {
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final ObjectProvider<LLMService> llmServiceProvider;

    public RagQueryPipeline(QueryRewriteService queryRewriteService,
                            IntentResolver intentResolver,
                            IntentGuidanceService guidanceService,
                            ObjectProvider<LLMService> llmServiceProvider) {
        this.queryRewriteService = queryRewriteService;
        this.intentResolver = intentResolver;
        this.guidanceService = guidanceService;
        this.llmServiceProvider = llmServiceProvider;
    }

    @RagTraceNode(name = "rag-query-pipeline", type = "PIPELINE")
    public RagQueryPlan prepare(String question, List<ChatMessage> memory) {
        List<String> steps = new ArrayList<>();
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, memory);
        steps.add("rewrite:" + rewriteResult.subQuestions().size());

        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);
        steps.add("intent:" + subIntents.size());
        IntentGroup intentGroup = intentResolver.mergeIntentGroup(subIntents);
        int topK = selectTopK(intentGroup);

        GuidanceDecision guidanceDecision = guidanceService.detectAmbiguity(rewriteResult.rewrittenQuestion(), subIntents);
        if (guidanceDecision.isPrompt()) {
            steps.add("guidance:prompt");
            return new RagQueryPlan(
                    question,
                    rewriteResult.rewrittenQuestion(),
                    topK,
                    subIntents,
                    intentGroup,
                    guidanceDecision,
                    guidanceDecision.getPrompt(),
                    steps
            );
        }

        boolean allSystemOnly = subIntents.stream()
                .allMatch(subQuestionIntent -> intentResolver.isSystemOnly(subQuestionIntent.nodeScores()));
        if (allSystemOnly) {
            String directAnswer = executeSystemOnly(rewriteResult.rewrittenQuestion(), memory, intentGroup);
            steps.add("system-only");
            return new RagQueryPlan(
                    question,
                    rewriteResult.rewrittenQuestion(),
                    topK,
                    subIntents,
                    intentGroup,
                    guidanceDecision,
                    directAnswer,
                    steps
            );
        }

        steps.add("route#" + topK);
        return new RagQueryPlan(
                question,
                rewriteResult.rewrittenQuestion(),
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

    private int selectTopK(IntentGroup intentGroup) {
        if (intentGroup == null || CollUtil.isEmpty(intentGroup.kbIntents())) {
            return 10;
        }
        return intentGroup.kbIntents().stream()
                .map(NodeScore::node)
                .filter(node -> node != null && node.topK != null && node.topK > 0)
                .mapToInt(node -> node.topK)
                .max()
                .orElse(10);
    }
}
