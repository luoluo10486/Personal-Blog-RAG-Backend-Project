package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.aop.ChatRateLimit;
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.rag.intent.IntentGroup;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.service.rag.intent.SubQuestionIntent;
import com.personalblog.ragbackend.knowledge.trace.RagTraceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class RAGChatServiceImpl implements RAGChatService {
    private final KnowledgeRagApplicationService knowledgeRagApplicationService;
    private final AIModelProperties aiModelProperties;
    private final StreamTaskManager streamTaskManager;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final KnowledgeAnswerGenerator answerGenerator;
    private final RagConversationService ragConversationService;
    private final StreamCallbackFactory streamCallbackFactory;

    public RAGChatServiceImpl(KnowledgeRagApplicationService knowledgeRagApplicationService,
                              AIModelProperties aiModelProperties,
                              StreamTaskManager streamTaskManager,
                              ObjectProvider<LLMService> llmServiceProvider,
                              KnowledgeAnswerGenerator answerGenerator,
                              RagConversationService ragConversationService,
                              StreamCallbackFactory streamCallbackFactory) {
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
        this.aiModelProperties = aiModelProperties;
        this.streamTaskManager = streamTaskManager;
        this.llmServiceProvider = llmServiceProvider;
        this.answerGenerator = answerGenerator;
        this.ragConversationService = ragConversationService;
        this.streamCallbackFactory = streamCallbackFactory;
    }

    @Override
    @ChatRateLimit
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId.trim();
        String taskId = StrUtil.blankToDefault(RagTraceContext.getTaskId(), IdUtil.getSnowflakeNextIdStr());
        LoginUser loginUser = UserContext.get();
        SseEmitterSender sender = new SseEmitterSender(emitter);
        executeStream(question, actualConversationId, Boolean.TRUE.equals(deepThinking), taskId, loginUser, sender);
    }

    @Override
    public void stopTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        streamTaskManager.cancel(taskId.trim());
    }

    private void executeStream(String question,
                               String conversationId,
                               boolean deepThinking,
                               String taskId,
                               LoginUser loginUser,
                               SseEmitterSender sender) {
        try {
            if (loginUser != null) {
                UserContext.set(loginUser);
            }

            PreparedRagAnswer prepared = knowledgeRagApplicationService.prepare(
                    new KnowledgeAskRequest(question, null, null, conversationId, deepThinking)
            );

            if (streamTaskManager.isCancelled(taskId)) {
                return;
            }

            StreamChatEventHandler callback = streamCallbackFactory.createChatEventHandler(
                    new StreamChatHandlerParams(
                            sender,
                            taskId,
                            conversationId,
                            ragConversationService,
                            loginUser,
                            question,
                            prepared.baseCode(),
                            prepared.citations().size(),
                            Math.max(1, aiModelProperties.getStream().getMessageChunkSize()),
                            streamTaskManager
                    )
            );

            if (prepared.hasDirectAnswer()) {
                callback.onContent(prepared.directAnswer());
                callback.onComplete();
                return;
            }

            if (!prepared.hasEvidence()) {
                callback.onContent("未检索到与问题相关的文档内容。");
                callback.onComplete();
                return;
            }

            LLMService llmService = llmServiceProvider.getIfAvailable();
            ChatRequest chatRequest = answerGenerator.buildRequest(
                    prepared.rewrittenQuestion(),
                    prepared.memory(),
                    prepared.chunks(),
                    extractKbIntents(prepared.intentGroup()),
                    extractMcpIntents(prepared.intentGroup()),
                    prepared.mcpContext(),
                    prepared.subQuestions(),
                    deepThinking
            );
            if (llmService == null || chatRequest == null) {
                String answer = knowledgeRagApplicationService.generateAnswer(prepared, deepThinking);
                callback.onContent(answer);
                callback.onComplete();
                return;
            }

            StreamCancellationHandle handle = llmService.streamChat(chatRequest, callback);
            streamTaskManager.bindHandle(taskId, handle);
        } catch (Throwable error) {
            sender.fail(error);
            streamTaskManager.unregister(taskId);
        } finally {
            UserContext.clear();
        }
    }

    private List<NodeScore> extractKbIntents(IntentGroup intentGroup) {
        return intentGroup == null || intentGroup.kbIntents() == null ? List.of() : intentGroup.kbIntents();
    }

    private List<NodeScore> extractMcpIntents(IntentGroup intentGroup) {
        return intentGroup == null || intentGroup.mcpIntents() == null ? List.of() : intentGroup.mcpIntents();
    }

    private List<String> extractSubQuestions(PreparedRagAnswer prepared) {
        if (prepared == null || prepared.plan() == null || prepared.plan().subIntents() == null) {
            return List.of();
        }
        return prepared.plan().subIntents().stream()
                .map(SubQuestionIntent::subQuestion)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
