package com.personalblog.ragbackend.infra.ai.convention;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {

    private List<ChatMessage> messages = new ArrayList<>();
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private Boolean thinking;
    private Boolean enableTools;

    public ChatRequest() {
    }

    public ChatRequest(List<ChatMessage> messages,
                       Double temperature,
                       Double topP,
                       Integer topK,
                       Integer maxTokens,
                       Boolean thinking,
                       Boolean enableTools) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = maxTokens;
        this.thinking = thinking;
        this.enableTools = enableTools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getThinking() {
        return thinking;
    }

    public void setThinking(Boolean thinking) {
        this.thinking = thinking;
    }

    public Boolean getEnableTools() {
        return enableTools;
    }

    public void setEnableTools(Boolean enableTools) {
        this.enableTools = enableTools;
    }

    public static final class Builder {
        private List<ChatMessage> messages = new ArrayList<>();
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxTokens;
        private Boolean thinking;
        private Boolean enableTools;

        private Builder() {
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder thinking(Boolean thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder enableTools(Boolean enableTools) {
            this.enableTools = enableTools;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(messages, temperature, topP, topK, maxTokens, thinking, enableTools);
        }
    }
}
