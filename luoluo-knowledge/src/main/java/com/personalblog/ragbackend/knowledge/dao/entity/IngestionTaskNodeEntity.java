package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_ingestion_task_node")
public class IngestionTaskNodeEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("task_id")
    public Long taskId;
    @TableField("pipeline_id")
    public Long pipelineId;
    @TableField("node_id")
    public String nodeId;
    @TableField("node_type")
    public String nodeType;
    @TableField("node_order")
    public Integer nodeOrder;
    @TableField("status")
    public String status;
    @TableField("duration_ms")
    public Long durationMs;
    @TableField("message")
    public String message;
    @TableField("error_message")
    public String errorMessage;
    @TableField("output_json")
    public String outputJson;
    @TableLogic
    @TableField("deleted")
    public Integer deleted;
    @TableField("created_at")
    public LocalDateTime createdAt;
    @TableField("updated_at")
    public LocalDateTime updatedAt;
}
