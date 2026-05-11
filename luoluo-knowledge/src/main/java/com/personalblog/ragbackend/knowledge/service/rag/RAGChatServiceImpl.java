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
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.dto.stream.MessageDelta;
import com.personalblog.ragbackend.knowledge.dto.stream.MetaPayload;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.rag.intent.IntentGroup;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class RAGChatServiceImpl implements RAGChatService {
    private static final String MESSAGE_TYPE_RESPONSE = "response";

    private final KnowledgeRagApplicationService knowledgeRagApplicationService;
    private final AIModelProperties aiModelProperties;
    private final StreamTaskManager streamTaskManager;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final KnowledgeAnswerGenerator answerGenerator;
    private final RagConversationService ragConversationService;

    public RAGChatServiceImpl(KnowledgeRagApplicationService knowledgeRagApplicationService,
                              AIModelProperties aiModelProperties,
                              StreamTaskManager streamTaskManager,
                              ObjectProvider<LLMService> llmServiceProvider,
                              KnowledgeAnswerGenerator answerGenerator,
                              RagConversationService ragConversationService) {
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
        this.aiModelProperties = aiModelProperties;
        this.streamTaskManager = streamTaskManager;
        this.llmServiceProvider = llmServiceProvider;
        this.answerGenerator = answerGenerator;
        this.ragConversationService = ragConversationService;
    }

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId.trim();
        String taskId = IdUtil.getSnowflakeNextIdStr();
        LoginUser loginUser = UserContext.get();
        SseEmitterSender sender = new SseEmitterSender(emitter);

        streamTaskManager.register(taskId);
        sender.sendEvent(SseEventType.META.value(), new MetaPayload(actualConversationId, taskId));
        CompletableFuture.runAsync(() -> executeStream(question, actualConversationId, Boolean.TRUE.equals(deepThinking), taskId, loginUser, sender));
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

            if (streamTaskManager.isCancelled(taskId)) {
                sendCancel(sender);
                streamTaskManager.unregister(taskId);
                return;
            }

            PreparedRagAnswer prepared = knowledgeRagApplicationService.prepare(
                    new KnowledgeAskRequest(question, null, null, conversationId, deepThinking)
            );

            if (streamTaskManager.isCancelled(taskId)) {
                sendCancel(sender);
                streamTaskManager.unregister(taskId);
                return;
            }

            if (prepared.hasDirectAnswer()) {
                ConversationPersistResult persistResult = ragConversationService.persistExchange(
                        conversationId,
                        question,
                        prepared.directAnswer(),
                        prepared.baseCode(),
                        prepared.citations().size()
                );
                sendChunkedAnswer(sender, prepared.directAnswer());
                sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(
                        persistResult.assistantMessageId(),
                        persistResult.conversationTitle()
                ));
                sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
                sender.complete();
                streamTaskManager.unregister(taskId);
                return;
            }

            if (!prepared.hasEvidence()) {
                String answer = knowledgeRagApplicationService.generateAnswer(prepared, deepThinking);
                ConversationPersistResult persistResult = ragConversationService.persistExchange(
                        conversationId,
                        question,
                        answer,
                        prepared.baseCode(),
                        prepared.citations().size()
                );
                sendChunkedAnswer(sender, answer);
                sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(
                        persistResult.assistantMessageId(),
                        persistResult.conversationTitle()
                ));
                sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
                sender.complete();
                streamTaskManager.unregister(taskId);
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
                    deepThinking
            );
            if (llmService == null || chatRequest == null) {
                String answer = knowledgeRagApplicationService.generateAnswer(prepared, deepThinking);
                ConversationPersistResult persistResult = ragConversationService.persistExchange(
                        conversationId,
                        question,
                        answer,
                        prepared.baseCode(),
                        prepared.citations().size()
                );
                sendChunkedAnswer(sender, answer);
                sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(
                        persistResult.assistantMessageId(),
                        persistResult.conversationTitle()
                ));
                sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
                sender.complete();
                streamTaskManager.unregister(taskId);
                return;
            }

            StreamChatEventHandler callback = new StreamChatEventHandler(
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
            );
            streamTaskManager.register(taskId, sender, callback::buildCancelPayload);
            StreamCancellationHandle handle = llmService.streamChat(chatRequest, callback);
            streamTaskManager.bindHandle(taskId, handle);
        } catch (Throwable error) {
            sender.fail(error);
            streamTaskManager.unregister(taskId);
        } finally {
            UserContext.clear();
        }
    }

    private void sendChunkedAnswer(SseEmitterSender sender, String answer) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        int chunkSize = Math.max(1, aiModelProperties.getStream().getMessageChunkSize());
        int index = 0;
        int count = 0;
        StringBuilder buffer = new StringBuilder();
        while (index < answer.length()) {
            int codePoint = answer.codePointAt(index);
            buffer.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
            count++;
            if (count >= chunkSize) {
                sender.sendEvent(SseEventType.MESSAGE.value(), new MessageDelta(MESSAGE_TYPE_RESPONSE, buffer.toString()));
                buffer.setLength(0);
                count = 0;
            }
        }
        if (!buffer.isEmpty()) {
            sender.sendEvent(SseEventType.MESSAGE.value(), new MessageDelta(MESSAGE_TYPE_RESPONSE, buffer.toString()));
        }
    }

    private void sendCancel(SseEmitterSender sender) {
        sender.sendEvent(SseEventType.CANCEL.value(), new CompletionPayload(null, null));
        sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
        sender.complete();
    }

    private List<NodeScore> extractKbIntents(IntentGroup intentGroup) {
        return intentGroup == null || intentGroup.kbIntents() == null ? List.of() : intentGroup.kbIntents();
    }

    private List<NodeScore> extractMcpIntents(IntentGroup intentGroup) {
        return intentGroup == null || intentGroup.mcpIntents() == null ? List.of() : intentGroup.mcpIntents();
    }
}
