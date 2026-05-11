package com.personalblog.ragbackend.rag.service;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.chat.StreamCallback;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.dto.stream.MessageDelta;
import com.personalblog.ragbackend.knowledge.dto.stream.MetaPayload;
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
    private String baseCode;
    private int citationCount;
    private final int chunkSize;
    private final StreamTaskManager taskManager;
    private final boolean userQuestionPrePersisted;
    private final boolean sendTitleOnComplete;
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
                                  StreamTaskManager taskManager,
                                  boolean userQuestionPrePersisted) {
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
        this.userQuestionPrePersisted = userQuestionPrePersisted;
        this.sendTitleOnComplete = shouldSendTitle();

        initialize();
    }

    public void updateEvidenceMetadata(String baseCode, int citationCount) {
        if (StrUtil.isNotBlank(baseCode)) {
            this.baseCode = baseCode.trim();
        }
        this.citationCount = Math.max(0, citationCount);
    }

    private void initialize() {
        sender.sendEvent(SseEventType.META.value(), new MetaPayload(conversationId, taskId));
        taskManager.register(taskId, sender, this::buildCancelPayload);
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
            result = persistAnswer();
        } finally {
            UserContext.clear();
        }
        sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(result.assistantMessageId(), resolveTitleForEvent()));
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
        if (loginUser != null) {
            UserContext.set(loginUser);
        }
        try {
            if (answer.isEmpty()) {
                return new CompletionPayload(null, resolveTitleForEvent());
            }
            ConversationPersistResult result = persistAnswer();
            return new CompletionPayload(result.assistantMessageId(), resolveTitleForEvent());
        } finally {
            UserContext.clear();
        }
    }

    private ConversationPersistResult persistAnswer() {
        String thinkingContent = StrUtil.isBlank(thinking.toString()) ? null : thinking.toString();
        if (userQuestionPrePersisted) {
            return ragConversationService.persistAssistantAnswer(
                    conversationId,
                    answer.toString(),
                    baseCode,
                    citationCount,
                    thinkingContent,
                    resolveThinkingDuration()
            );
        }
        return ragConversationService.persistExchange(
                conversationId,
                question,
                answer.toString(),
                baseCode,
                citationCount,
                thinkingContent,
                resolveThinkingDuration()
        );
    }

    private boolean shouldSendTitle() {
        return StrUtil.isBlank(ragConversationService.findConversationTitle(conversationId));
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

    private String resolveTitleForEvent() {
        if (!sendTitleOnComplete) {
            return null;
        }
        return ragConversationService.findConversationTitle(conversationId);
    }
}
