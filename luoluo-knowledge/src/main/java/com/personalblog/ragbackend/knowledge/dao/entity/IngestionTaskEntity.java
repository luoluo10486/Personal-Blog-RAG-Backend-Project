package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_ingestion_task")
public class IngestionTaskEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("pipeline_id")
    public Long pipelineId;
    @TableField("kb_id")
    public Long kbId;
    @TableField("doc_id")
    public Long docId;
    @TableField("source_type")
    public String sourceType;
    @TableField("source_location")
    public String sourceLocation;
    @TableField("source_file_name")
    public String sourceFileName;
    @TableField("status")
    public String status;
    @TableField("chunk_count")
    public Integer chunkCount;
    @TableField("error_message")
    public String errorMessage;
    @TableField("logs_json")
    public String logsJson;
    @TableField("metadata_json")
    public String metadataJson;
    @TableField("started_at")
    public LocalDateTime startedAt;
    @TableField("completed_at")
    public LocalDateTime completedAt;
    @TableField("created_by")
    public Long createdBy;
    @TableField("updated_by")
    public Long updatedBy;
    @TableLogic
    @TableField("deleted")
    public Integer deleted;
    @TableField("created_at")
    public LocalDateTime createdAt;
    @TableField("updated_at")
    public LocalDateTime updatedAt;
}
