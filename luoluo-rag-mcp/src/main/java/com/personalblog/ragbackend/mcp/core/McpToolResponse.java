package com.personalblog.ragbackend.mcp.core;

import java.util.HashMap;
import java.util.Map;

public class McpToolResponse {

    private boolean success = true;
    private String toolId;
    private Map<String, Object> data = new HashMap<>();
    private String textResult;
    private String errorMessage;
    private String errorCode;
    private long costMs;

    public static McpToolResponse success(String toolId, String textResult) {
        McpToolResponse response = new McpToolResponse();
        response.setSuccess(true);
        response.setToolId(toolId);
        response.setTextResult(textResult);
        return response;
    }

    public static McpToolResponse success(String toolId, String textResult, Map<String, Object> data) {
        McpToolResponse response = new McpToolResponse();
        response.setSuccess(true);
        response.setToolId(toolId);
        response.setTextResult(textResult);
        response.setData(data);
        return response;
    }

    public static McpToolResponse error(String toolId, String errorCode, String errorMessage) {
        McpToolResponse response = new McpToolResponse();
        response.setSuccess(false);
        response.setToolId(toolId);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new HashMap<>() : data;
    }

    public String getTextResult() {
        return textResult;
    }

    public void setTextResult(String textResult) {
        this.textResult = textResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }
}
