package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.dto.stream.MessageDelta;
import com.personalblog.ragbackend.knowledge.dto.stream.MetaPayload;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * 对齐 RAgent SSE 协议形状的流式聊天服务。
 */
@Service
public class RAGChatServiceImpl implements RAGChatService {
    private static final String MESSAGE_TYPE_RESPONSE = "response";

    private final KnowledgeRagApplicationService knowledgeRagApplicationService;
    private final AIModelProperties aiModelProperties;
    private final StreamTaskManager streamTaskManager;

    public RAGChatServiceImpl(KnowledgeRagApplicationService knowledgeRagApplicationService,
                              AIModelProperties aiModelProperties,
                              StreamTaskManager streamTaskManager) {
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
        this.aiModelProperties = aiModelProperties;
        this.streamTaskManager = streamTaskManager;
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

        CompletableFuture.runAsync(() -> executeStream(question, actualConversationId, deepThinking, taskId, loginUser, sender));
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
                               Boolean deepThinking,
                               String taskId,
                               LoginUser loginUser,
                               SseEmitterSender sender) {
        try {
            if (loginUser != null) {
                UserContext.set(loginUser);
            }

            if (streamTaskManager.isCancelled(taskId)) {
                sendCancel(sender);
                return;
            }

            KnowledgeAskResponse response = knowledgeRagApplicationService.ask(
                    new KnowledgeAskRequest(question, null, null, conversationId)
            );

            if (streamTaskManager.isCancelled(taskId)) {
                sendCancel(sender);
                return;
            }

            sendChunkedAnswer(sender, response.answer());
            sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(null, null));
            sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
            sender.complete();
        } catch (Throwable error) {
            sender.fail(error);
        } finally {
            streamTaskManager.unregister(taskId);
            UserContext.clear();
        }
    }

    private void sendChunkedAnswer(SseEmitterSender sender, String answer) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        int chunkSize = Math.max(1, aiModelProperties.getStream().getMessageChunkSize());
        int index = 0;
        while (index < answer.length()) {
            int end = Math.min(answer.length(), index + chunkSize);
            sender.sendEvent(
                    SseEventType.MESSAGE.value(),
                    new MessageDelta(MESSAGE_TYPE_RESPONSE, answer.substring(index, end))
            );
            index = end;
        }
    }

    private void sendCancel(SseEmitterSender sender) {
        sender.sendEvent(SseEventType.CANCEL.value(), new CompletionPayload(null, null));
        sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
        sender.complete();
    }
}
