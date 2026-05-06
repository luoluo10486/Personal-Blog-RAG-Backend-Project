package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_trace_node")
public class RagTraceNodeEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("trace_id")
    public String traceId;
    @TableField("node_id")
    public String nodeId;
    @TableField("parent_node_id")
    public String parentNodeId;
    @TableField("depth")
    public Integer depth;
    @TableField("node_type")
    public String nodeType;
    @TableField("node_name")
    public String nodeName;
    @TableField("class_name")
    public String className;
    @TableField("method_name")
    public String methodName;
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
