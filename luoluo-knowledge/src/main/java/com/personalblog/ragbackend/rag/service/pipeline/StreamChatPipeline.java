package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.memory.ConversationMemoryService;
import com.personalblog.ragbackend.rag.core.prompt.PromptContext;
import com.personalblog.ragbackend.rag.core.prompt.RAGPromptService;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalContext;
import com.personalblog.ragbackend.rag.core.retrieve.RetrievalEngine;
import com.personalblog.ragbackend.rag.service.StreamChatEventHandler;
import com.personalblog.ragbackend.rag.service.StreamTaskManager;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StreamChatPipeline {
    private final ConversationMemoryService memoryService;
    private final RagQueryPipeline queryPipeline;
    private final RetrievalEngine retrievalEngine;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final RAGPromptService promptBuilder;
    private final StreamTaskManager taskManager;

    public StreamChatPipeline(ConversationMemoryService memoryService,
                              RagQueryPipeline queryPipeline,
                              RetrievalEngine retrievalEngine,
                              ObjectProvider<LLMService> llmServiceProvider,
                              RAGPromptService promptBuilder,
                              StreamTaskManager taskManager) {
        this.memoryService = memoryService;
        this.queryPipeline = queryPipeline;
        this.retrievalEngine = retrievalEngine;
        this.llmServiceProvider = llmServiceProvider;
        this.promptBuilder = promptBuilder;
        this.taskManager = taskManager;
    }

    @RagTraceNode(name = "stream-chat-pipeline", type = "PIPELINE")
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        RagQueryPlan queryPlan = queryPipeline.prepare(ctx.getQuestion(), ctx.getHistory());

        if (queryPlan.hasGuidancePrompt()) {
            emitContentAndComplete(ctx, queryPlan.guidanceDecision().getPrompt());
            return;
        }
        if (queryPlan.hasDirectAnswer()) {
            emitContentAndComplete(ctx, queryPlan.directAnswer());
            return;
        }

        RetrievalContext retrievalContext = retrieve(queryPlan);
        if (handleEmptyRetrieval(ctx, retrievalContext)) {
            return;
        }

        streamRagResponse(ctx, retrievalContext, queryPlan);
    }

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private void emitContentAndComplete(StreamChatContext ctx, String content) {
        StreamChatEventHandler callback = ctx.getCallback();
        callback.onContent(content);
        callback.onComplete();
    }

    private RetrievalContext retrieve(RagQueryPlan queryPlan) {
        return retrievalEngine.retrieve(queryPlan.subIntents(), queryPlan.topK());
    }

    private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalContext) {
        if (!retrievalContext.isEmpty()) {
            return false;
        }
        emitContentAndComplete(ctx, "未检索到与问题相关的文档内容。");
        return true;
    }

    private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalContext, RagQueryPlan queryPlan) {
        IntentGroup mergedGroup = queryPlan.intentGroup();
        List<String> subQuestions = queryPlan.subQuestions();
        PromptContext promptContext = PromptContext.builder()
                .question(queryPlan.rewrittenQuestion())
                .mcpContext(retrievalContext.getMcpContext())
                .kbContext(retrievalContext.getKbContext())
                .mcpIntents(mergedGroup.mcpIntents())
                .kbIntents(mergedGroup.kbIntents())
                .intentChunks(retrievalContext.getIntentChunks())
                .build();
        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                ctx.getHistory(),
                queryPlan.rewrittenQuestion(),
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
            emitContentAndComplete(ctx, retrievalContext.hasKb() ? retrievalContext.getKbContext() : retrievalContext.getMcpContext());
            return;
        }
        StreamCancellationHandle handle = llmService.streamChat(chatRequest, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }
}
