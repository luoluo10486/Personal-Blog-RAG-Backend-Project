package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.chat.StreamCallback;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.dto.stream.MessageDelta;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;

public class StreamChatEventHandler implements StreamCallback {
    private static final String TYPE_THINK = "think";
    private static final String TYPE_RESPONSE = "response";

    private final SseEmitterSender sender;
    private final String taskId;
    private final String conversationId;
    private final RagConversationService ragConversationService;
    private final LoginUser loginUser;
    private final String question;
    private final String baseCode;
    private final int citationCount;
    private final int chunkSize;
    private final StreamTaskManager taskManager;
    private final StringBuilder answer = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private long thinkingStartMs;
    private int thinkingDurationSeconds;

    public StreamChatEventHandler(SseEmitterSender sender,
                                  String taskId,
                                  String conversationId,
                                  RagConversationService ragConversationService,
                                  LoginUser loginUser,
                                  String question,
                                  String baseCode,
                                  int citationCount,
                                  int chunkSize,
                                  StreamTaskManager taskManager) {
        this.sender = sender;
        this.taskId = taskId;
        this.conversationId = conversationId;
        this.ragConversationService = ragConversationService;
        this.loginUser = loginUser;
        this.question = question;
        this.baseCode = baseCode;
        this.citationCount = citationCount;
        this.chunkSize = chunkSize;
        this.taskManager = taskManager;
    }

    @Override
    public void onContent(String content) {
        if (taskManager.isCancelled(taskId) || StrUtil.isBlank(content)) {
            return;
        }
        if (thinkingStartMs > 0 && thinkingDurationSeconds == 0) {
            thinkingDurationSeconds = Math.max(1, Math.round((System.currentTimeMillis() - thinkingStartMs) / 1000.0f));
        }
        answer.append(content);
        sendChunked(TYPE_RESPONSE, content);
    }

    @Override
    public void onThinking(String content) {
        if (taskManager.isCancelled(taskId) || StrUtil.isBlank(content)) {
            return;
        }
        if (thinkingStartMs == 0L) {
            thinkingStartMs = System.currentTimeMillis();
        }
        thinking.append(content);
        sendChunked(TYPE_THINK, content);
    }

    @Override
    public void onComplete() {
        if (taskManager.isCancelled(taskId)) {
            return;
        }
        ConversationPersistResult result;
        if (loginUser != null) {
            UserContext.set(loginUser);
        }
        try {
            result = ragConversationService.persistExchange(
                    conversationId,
                    question,
                    answer.toString(),
                    baseCode,
                    citationCount,
                    StrUtil.isBlank(thinking.toString()) ? null : thinking.toString(),
                    resolveThinkingDuration()
            );
        } finally {
            UserContext.clear();
        }
        sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(result.assistantMessageId(), result.conversationTitle()));
        sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
        taskManager.unregister(taskId);
        sender.complete();
    }

    @Override
    public void onError(Throwable error) {
        if (taskManager.isCancelled(taskId)) {
            return;
        }
        taskManager.unregister(taskId);
        sender.fail(error);
    }

    public CompletionPayload buildCancelPayload() {
        if (answer.isEmpty()) {
            return new CompletionPayload(null, null);
        }
        if (loginUser != null) {
            UserContext.set(loginUser);
        }
        try {
            ConversationPersistResult result = ragConversationService.persistExchange(
                    conversationId,
                    question,
                    answer.toString(),
                    baseCode,
                    citationCount,
                    StrUtil.isBlank(thinking.toString()) ? null : thinking.toString(),
                    resolveThinkingDuration()
            );
            return new CompletionPayload(result.assistantMessageId(), result.conversationTitle());
        } finally {
            UserContext.clear();
        }
    }

    private void sendChunked(String type, String content) {
        int index = 0;
        int count = 0;
        StringBuilder buffer = new StringBuilder();
        while (index < content.length()) {
            int codePoint = content.codePointAt(index);
            buffer.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
            count++;
            if (count >= chunkSize) {
                sender.sendEvent(SseEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
                buffer.setLength(0);
                count = 0;
            }
        }
        if (!buffer.isEmpty()) {
            sender.sendEvent(SseEventType.MESSAGE.value(), new MessageDelta(type, buffer.toString()));
        }
    }

    private Integer resolveThinkingDuration() {
        if (thinkingDurationSeconds > 0) {
            return thinkingDurationSeconds;
        }
        if (thinkingStartMs == 0L) {
            return null;
        }
        thinkingDurationSeconds = Math.max(1, Math.round((System.currentTimeMillis() - thinkingStartMs) / 1000.0f));
        return thinkingDurationSeconds;
    }
}
