package com.personalblog.ragbackend.infra.ai.convention;

public class ChatMessage {

    private Role role;
    private String content;
    private String thinkingContent;
    private Integer thinkingDuration;

    public ChatMessage() {
    }

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage assistant(String content, String thinkingContent) {
        return assistant(content, thinkingContent, null);
    }

    public static ChatMessage assistant(String content, String thinkingContent, Integer thinkingDuration) {
        ChatMessage message = new ChatMessage(Role.ASSISTANT, content);
        message.setThinkingContent(thinkingContent);
        message.setThinkingDuration(thinkingDuration);
        return message;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getThinkingContent() {
        return thinkingContent;
    }

    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }

    public Integer getThinkingDuration() {
        return thinkingDuration;
    }

    public void setThinkingDuration(Integer thinkingDuration) {
        this.thinkingDuration = thinkingDuration;
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT;

        public static Role fromString(String value) {
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unsupported role: " + value);
        }
    }
}
