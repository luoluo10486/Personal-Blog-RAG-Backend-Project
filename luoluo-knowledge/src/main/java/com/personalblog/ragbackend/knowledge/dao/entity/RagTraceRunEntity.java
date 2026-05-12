package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_rag_trace_run")
public class RagTraceRunEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("trace_id")
    public String traceId;
    @TableField("trace_name")
    public String traceName;
    @TableField("entry_method")
    public String entryMethod;
    @TableField("conversation_id")
    public String conversationId;
    @TableField("task_id")
    public String taskId;
    @TableField("user_id")
    public Long userId;
    @TableField("status")
    public String status;
    @TableField("error_message")
    public String errorMessage;
    @TableField("started_at")
    public LocalDateTime startedAt;
    @TableField("ended_at")
    public LocalDateTime endedAt;
    @TableField("duration_ms")
    public Long durationMs;
    @TableField("extra_data")
    public String extraData;
    @TableLogic
    @TableField("deleted")
    public Integer deleted;
    @TableField("created_at")
    public LocalDateTime createdAt;
    @TableField("updated_at")
    public LocalDateTime updatedAt;
}
