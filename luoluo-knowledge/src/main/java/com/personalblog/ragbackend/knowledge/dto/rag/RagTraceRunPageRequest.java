package com.personalblog.ragbackend.knowledge.dto.rag;

public class RagTraceRunPageRequest {
    private long current = 1;
    private long size = 10;
    private String traceId;
    private String conversationId;
    private String taskId;
    private String status;

    public long getCurrent() { return current; }
    public void setCurrent(long current) { this.current = current <= 0 ? 1 : current; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size <= 0 ? 10 : size; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
