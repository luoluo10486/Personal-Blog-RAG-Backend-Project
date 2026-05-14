package com.personalblog.ragbackend.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.rag.core.guidance.GuidanceDecision;
import com.personalblog.ragbackend.rag.core.guidance.IntentGuidanceService;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.RagIntentResolver;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.memory.ConversationMemoryService;
import com.personalblog.ragbackend.rag.core.prompt.PromptContext;
import com.personalblog.ragbackend.rag.core.prompt.RAGPromptService;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalContext;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalEngine;
import com.personalblog.ragbackend.rag.core.rewrite.QueryRewriteService;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import com.personalblog.ragbackend.rag.service.StreamChatEventHandler;
import com.personalblog.ragbackend.rag.service.StreamTaskManager;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StreamChatPipeline {
    private static final String CHAT_SYSTEM_PROMPT_PATH = "prompt/chat-system.st";

    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final RagIntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;
    private final KnowledgeProperties knowledgeProperties;

    public StreamChatPipeline(ConversationMemoryService memoryService,
                              QueryRewriteService queryRewriteService,
                              RagIntentResolver intentResolver,
                              IntentGuidanceService guidanceService,
                              RetrievalEngine retrievalEngine,
                              ObjectProvider<LLMService> llmServiceProvider,
                              RAGPromptService promptBuilder,
                              PromptTemplateLoader promptTemplateLoader,
                              StreamTaskManager taskManager,
                              KnowledgeProperties knowledgeProperties) {
        this.memoryService = memoryService;
        this.queryRewriteService = queryRewriteService;
        this.intentResolver = intentResolver;
        this.guidanceService = guidanceService;
        this.retrievalEngine = retrievalEngine;
        this.llmServiceProvider = llmServiceProvider;
        this.promptBuilder = promptBuilder;
        this.promptTemplateLoader = promptTemplateLoader;
        this.taskManager = taskManager;
        this.knowledgeProperties = knowledgeProperties;
    }

    @RagTraceNode(name = "stream-chat-pipeline", type = "PIPELINE")
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        rewriteQuery(ctx);
        resolveIntents(ctx);

        if (handleGuidance(ctx)) {
            return;
        }
        if (handleSystemOnly(ctx)) {
            return;
        }

        RetrievalContext retrievalContext = retrieve(ctx);
        if (handleEmptyRetrieval(ctx, retrievalContext)) {
            return;
        }

        streamRagResponse(ctx, retrievalContext);
    }

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        ctx.setRewriteResult(rewriteResult);
    }

    private void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
    }

    private boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getSubIntents()
        );
        if (!decision.isPrompt()) {
            return false;
        }
        StreamChatEventHandler callback = ctx.getCallback();
        callback.onContent(decision.getPrompt());
        callback.onComplete();
        return true;
    }

    private boolean handleSystemOnly(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = ctx.getSubIntents();
        boolean allSystemOnly = CollUtil.isNotEmpty(subIntents)
                && subIntents.stream().allMatch(intent -> intentResolver.isSystemOnly(intent.nodeScores()));
        if (!allSystemOnly) {
            return false;
        }
        String customPrompt = subIntents.stream()
                .flatMap(intent -> intent.nodeScores().stream())
                .map(NodeScore::node)
                .filter(node -> node != null && StrUtil.isNotBlank(node.promptTemplate))
                .map(node -> node.promptTemplate)
                .findFirst()
                .orElse(null);
        StreamCancellationHandle handle = streamSystemResponse(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getHistory(),
                customPrompt,
                ctx.getCallback()
        );
        if (handle != null) {
            taskManager.bindHandle(ctx.getTaskId(), handle);
        }
        return true;
    }

    private RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(
                ctx.getSubIntents(),
                ctx.getBaseCode(),
                resolveTopK(ctx.getSubIntents(), ctx.getTopK()),
                ctx.getConversationId(),
                ctx.getUserIdText()
        );
    }

    private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalContext) {
        if (!retrievalContext.isEmpty()) {
            return false;
        }
        StreamChatEventHandler callback = ctx.getCallback();
        callback.onContent("未检索到与问题相关的文档内容。");
        callback.onComplete();
        return true;
    }

    private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalContext) {
        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());
        List<String> subQuestions = ctx.getRewriteResult().subQuestions();
        PromptContext promptContext = PromptContext.builder()
                .question(ctx.getRewriteResult().rewrittenQuestion())
                .mcpContext(retrievalContext.getMcpContext())
                .kbContext(retrievalContext.getKbContext())
                .mcpIntents(mergedGroup.mcpIntents())
                .kbIntents(mergedGroup.kbIntents())
                .intentChunks(retrievalContext.getIntentChunks())
                .build();
        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                ctx.getRewriteResult().rewrittenQuestion(),
                subQuestions
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(ctx.isDeepThinking())
                .temperature(retrievalContext.hasMcp() ? 0.3D : 0D)
                .topP(retrievalContext.hasMcp() ? 0.8D : 1D)
                .build();
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            ctx.getCallback().onContent(retrievalContext.hasKb() ? retrievalContext.getKbContext() : retrievalContext.getMcpContext());
            ctx.getCallback().onComplete();
            return;
        }
        StreamCancellationHandle handle = llmService.streamChat(chatRequest, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

    private int countChunks(RetrievalContext retrievalContext) {
        if (retrievalContext == null || retrievalContext.getIntentChunks() == null || retrievalContext.getIntentChunks().isEmpty()) {
            return 0;
        }
        return (int) retrievalContext.getIntentChunks().values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getId()))
                .map(RetrievedChunk -> RetrievedChunk.getId())
                .distinct()
                .count();
    }

    private StreamCancellationHandle streamSystemResponse(String question,
                                                          List<ChatMessage> history,
                                                          String customPrompt,
                                                          StreamChatEventHandler callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            callback.onContent(StrUtil.blankToDefault(systemPrompt, question));
            callback.onComplete();
            return null;
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        return llmService.streamChat(request, callback);
    }

    private int resolveTopK(List<SubQuestionIntent> subIntents, int fallbackTopK) {
        int defaultTopK = fallbackTopK > 0 ? fallbackTopK : knowledgeProperties.getSearch().getTopK();
        return subIntents.stream()
                .flatMap(intent -> intent.nodeScores().stream())
                .map(NodeScore::node)
                .filter(node -> node != null && node.topK != null && node.topK > 0)
                .map(node -> node.topK)
                .max(Integer::compareTo)
                .orElse(defaultTopK);
    }
}
