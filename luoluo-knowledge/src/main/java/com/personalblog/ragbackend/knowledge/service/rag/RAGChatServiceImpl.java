package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.knowledge.aop.ChatRateLimit;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.service.rag.pipeline.StreamChatContext;
import com.personalblog.ragbackend.knowledge.service.rag.pipeline.StreamChatPipeline;
import com.personalblog.ragbackend.knowledge.trace.RagTraceContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RAGChatServiceImpl implements RAGChatService {
    private final StreamChatPipeline chatPipeline;
    private final AIModelProperties aiModelProperties;
    private final KnowledgeProperties knowledgeProperties;
    private final StreamTaskManager streamTaskManager;
    private final RagConversationService ragConversationService;
    private final StreamCallbackFactory streamCallbackFactory;

    public RAGChatServiceImpl(StreamChatPipeline chatPipeline,
                              AIModelProperties aiModelProperties,
                              KnowledgeProperties knowledgeProperties,
                              StreamTaskManager streamTaskManager,
                              RagConversationService ragConversationService,
                              StreamCallbackFactory streamCallbackFactory) {
        this.chatPipeline = chatPipeline;
        this.aiModelProperties = aiModelProperties;
        this.knowledgeProperties = knowledgeProperties;
        this.streamTaskManager = streamTaskManager;
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
        String userIdText = StrUtil.blankToDefault(UserContext.getUserId(), "");
        SseEmitterSender sender = new SseEmitterSender(emitter);

        StreamChatEventHandler callback = streamCallbackFactory.createChatEventHandler(
                new StreamChatHandlerParams(
                        sender,
                        taskId,
                        actualConversationId,
                        ragConversationService,
                        loginUser,
                        question,
                        knowledgeProperties.getDefaultBaseCode(),
                        0,
                        Math.max(1, aiModelProperties.getStream().getMessageChunkSize()),
                        streamTaskManager,
                        true
                )
        );

        StreamChatContext context = StreamChatContext.builder()
                .question(question)
                .conversationId(actualConversationId)
                .taskId(taskId)
                .deepThinking(Boolean.TRUE.equals(deepThinking))
                .userId(resolveUserId(userIdText))
                .userIdText(userIdText)
                .baseCode(knowledgeProperties.getDefaultBaseCode())
                .topK(knowledgeProperties.getSearch().getTopK())
                .callback(callback)
                .build();

        try {
            if (loginUser != null) {
                UserContext.set(loginUser);
            }
            chatPipeline.execute(context);
        } catch (Throwable error) {
            callback.onError(error);
        } finally {
            UserContext.clear();
        }
    }

    @Override
    public void stopTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        streamTaskManager.cancel(taskId.trim());
    }

    private Long resolveUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
