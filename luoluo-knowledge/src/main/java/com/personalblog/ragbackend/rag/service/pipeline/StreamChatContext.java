package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.rag.service.StreamChatEventHandler;

import java.util.List;

public class StreamChatContext {
    private String question;
    private String conversationId;
    private String taskId;
    private boolean deepThinking;
    private Long userId;
    private String userIdText;
    private StreamChatEventHandler callback;
    private List<ChatMessage> history = List.of();

    public static Builder builder() {
        return new Builder();
    }

    public String getQuestion() {
        return question;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getTaskId() {
        return taskId;
    }

    public boolean isDeepThinking() {
        return deepThinking;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserIdText() {
        return userIdText;
    }

    public StreamChatEventHandler getCallback() {
        return callback;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history == null ? List.of() : history;
    }

    public static final class Builder {
        private final StreamChatContext context = new StreamChatContext();

        public Builder question(String question) {
            context.question = question;
            return this;
        }

        public Builder conversationId(String conversationId) {
            context.conversationId = conversationId;
            return this;
        }

        public Builder taskId(String taskId) {
            context.taskId = taskId;
            return this;
        }

        public Builder deepThinking(boolean deepThinking) {
            context.deepThinking = deepThinking;
            return this;
        }

        public Builder userId(Long userId) {
            context.userId = userId;
            return this;
        }

        public Builder userIdText(String userIdText) {
            context.userIdText = userIdText;
            return this;
        }

        public Builder callback(StreamChatEventHandler callback) {
            context.callback = callback;
            return this;
        }

        public StreamChatContext build() {
            return context;
        }
    }
}
