package com.personalblog.ragbackend.knowledge.service.rag.pipeline;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeQueryRewriteResult;
import com.personalblog.ragbackend.knowledge.service.rag.StreamChatEventHandler;
import com.personalblog.ragbackend.knowledge.service.rag.intent.SubQuestionIntent;

import java.util.List;

public class StreamChatContext {
    private String question;
    private String conversationId;
    private String taskId;
    private boolean deepThinking;
    private Long userId;
    private String userIdText;
    private String baseCode;
    private int topK;
    private StreamChatEventHandler callback;
    private List<ChatMessage> history = List.of();
    private KnowledgeQueryRewriteResult rewriteResult;
    private List<SubQuestionIntent> subIntents = List.of();

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

    public String getBaseCode() {
        return baseCode;
    }

    public int getTopK() {
        return topK;
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

    public KnowledgeQueryRewriteResult getRewriteResult() {
        return rewriteResult;
    }

    public void setRewriteResult(KnowledgeQueryRewriteResult rewriteResult) {
        this.rewriteResult = rewriteResult;
    }

    public List<SubQuestionIntent> getSubIntents() {
        return subIntents;
    }

    public void setSubIntents(List<SubQuestionIntent> subIntents) {
        this.subIntents = subIntents == null ? List.of() : subIntents;
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

        public Builder baseCode(String baseCode) {
            context.baseCode = baseCode;
            return this;
        }

        public Builder topK(int topK) {
            context.topK = topK;
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
