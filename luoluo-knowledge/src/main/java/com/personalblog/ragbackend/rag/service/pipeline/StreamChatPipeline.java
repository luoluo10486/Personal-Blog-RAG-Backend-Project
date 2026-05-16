package com.personalblog.ragbackend.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.guidance.GuidanceDecision;
import com.personalblog.ragbackend.rag.core.guidance.IntentGuidanceService;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.IntentResolver;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.memory.ConversationMemoryService;
import com.personalblog.ragbackend.rag.core.prompt.PromptContext;
import com.personalblog.ragbackend.rag.core.prompt.RAGPromptService;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalContext;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalEngine;
import com.personalblog.ragbackend.rag.service.StreamChatEventHandler;
import com.personalblog.ragbackend.rag.service.StreamTaskManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StreamChatPipeline {
    private final SearchChannelProperties searchProperties;
    private final ConversationMemoryService memoryService;
    private final RagQueryPipeline queryPipeline;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;

    public StreamChatPipeline(SearchChannelProperties searchProperties,
                              ConversationMemoryService memoryService,
                              RagQueryPipeline queryPipeline,
                              IntentResolver intentResolver,
                              IntentGuidanceService guidanceService,
                              RetrievalEngine retrievalEngine,
                              ObjectProvider<LLMService> llmServiceProvider,
                              RAGPromptService promptBuilder,
                              PromptTemplateLoader promptTemplateLoader,
                              StreamTaskManager taskManager) {
        this.searchProperties = searchProperties;
        this.memoryService = memoryService;
        this.queryPipeline = queryPipeline;
        this.intentResolver = intentResolver;
        this.guidanceService = guidanceService;
        this.retrievalEngine = retrievalEngine;
        this.llmServiceProvider = llmServiceProvider;
        this.promptBuilder = promptBuilder;
        this.promptTemplateLoader = promptTemplateLoader;
        this.taskManager = taskManager;
    }

    @RagTraceNode(name = "stream-chat-pipeline", type = "PIPELINE")
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        RagQueryPlan queryPlan = queryPipeline.prepare(ctx.getQuestion(), ctx.getHistory());
        RewriteResult rewriteResult = queryPlan.rewriteResult();
        List<SubQuestionIntent> subIntents = queryPlan.subIntents();

        if (handleGuidance(ctx, rewriteResult, subIntents)) {
            return;
        }
        if (handleSystemOnly(ctx, rewriteResult, subIntents, queryPlan.intentGroup())) {
            return;
        }

        RetrievalContext retrievalContext = retrieve(subIntents);
        if (handleEmptyRetrieval(ctx, retrievalContext)) {
            return;
        }

        streamRagResponse(ctx, retrievalContext, queryPlan.intentGroup(), rewriteResult);
    }

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private boolean handleGuidance(StreamChatContext ctx,
                                   RewriteResult rewriteResult,
                                   List<SubQuestionIntent> subIntents) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(
                rewriteResult == null ? "" : rewriteResult.rewrittenQuestion(),
                subIntents
        );
        if (!decision.isPrompt()) {
            return false;
        }
        emitContentAndComplete(ctx, decision.getPrompt());
        return true;
    }

    private boolean handleSystemOnly(StreamChatContext ctx,
                                     RewriteResult rewriteResult,
                                     List<SubQuestionIntent> subIntents,
                                     IntentGroup intentGroup) {
        if (CollUtil.isEmpty(subIntents) || !subIntents.stream().allMatch(subQuestionIntent -> intentResolver.isSystemOnly(subQuestionIntent.nodeScores()))) {
            return false;
        }

        String customPrompt = resolveSystemPrompt(intentGroup);
        streamSystemResponse(
                rewriteResult == null ? ctx.getQuestion() : rewriteResult.rewrittenQuestion(),
                ctx.getHistory(),
                customPrompt,
                ctx.getCallback(),
                ctx.getTaskId()
        );
        return true;
    }

    private void emitContentAndComplete(StreamChatContext ctx, String content) {
        StreamChatEventHandler callback = ctx.getCallback();
        callback.onContent(content);
        callback.onComplete();
    }

    private RetrievalContext retrieve(List<SubQuestionIntent> subIntents) {
        return retrievalEngine.retrieve(subIntents, searchProperties.getDefaultTopK());
    }

    private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalContext) {
        if (!retrievalContext.isEmpty()) {
            return false;
        }
        emitContentAndComplete(ctx, "未检索到与问题相关的文档内容。");
        return true;
    }

    private void streamRagResponse(StreamChatContext ctx,
                                   RetrievalContext retrievalContext,
                                   IntentGroup mergedGroup,
                                   RewriteResult rewriteResult) {
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult == null ? "" : rewriteResult.rewrittenQuestion())
                .mcpContext(retrievalContext.getMcpContext())
                .kbContext(retrievalContext.getKbContext())
                .mcpIntents(mergedGroup == null ? List.of() : mergedGroup.mcpIntents())
                .kbIntents(mergedGroup == null ? List.of() : mergedGroup.kbIntents())
                .intentChunks(retrievalContext.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                rewriteResult == null ? "" : rewriteResult.rewrittenQuestion(),
                rewriteResult == null ? List.of() : rewriteResult.subQuestions()
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(ctx.isDeepThinking())
                .temperature(retrievalContext.hasMcp() ? 0.3D : 0D)
                .topP(retrievalContext.hasMcp() ? 0.8D : 1D)
                .build();

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            emitContentAndComplete(ctx, retrievalContext.hasKb() ? retrievalContext.getKbContext() : retrievalContext.getMcpContext());
            return;
        }
        StreamCancellationHandle handle = llmService.streamChat(chatRequest, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

    private void streamSystemResponse(String question,
                                      List<ChatMessage> history,
                                      String customPrompt,
                                      StreamChatEventHandler callback,
                                      String taskId) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(RAGConstant.CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            callback.onContent(StrUtil.isNotBlank(systemPrompt) ? systemPrompt : question);
            callback.onComplete();
            return;
        }

        StreamCancellationHandle handle = llmService.streamChat(request, callback);
        taskManager.bindHandle(taskId, handle);
    }

    private String resolveSystemPrompt(IntentGroup intentGroup) {
        if (intentGroup == null || CollUtil.isEmpty(intentGroup.kbIntents())) {
            return "";
        }
        return intentGroup.kbIntents().stream()
                .map(NodeScore::node)
                .filter(node -> node != null && node.isSystem())
                .map(node -> {
                    if (StrUtil.isNotBlank(node.getPromptTemplate())) {
                        return node.getPromptTemplate();
                    }
                    if (StrUtil.isNotBlank(node.getPromptSnippet())) {
                        return node.getPromptSnippet();
                    }
                    return node.getFullPath();
                })
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("");
    }
}


