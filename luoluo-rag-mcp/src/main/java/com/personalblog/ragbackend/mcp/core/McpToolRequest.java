package com.personalblog.ragbackend.mcp.core;

import java.util.HashMap;
import java.util.Map;

public class McpToolRequest {

    private String toolId;
    private String userId;
    private String conversationId;
    private String userQuestion;
    private Map<String, Object> parameters = new HashMap<>();

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
    }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        Object value = parameters.get(key);
        return value != null ? (T) value : null;
    }

    public String getStringParameter(String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }
}
