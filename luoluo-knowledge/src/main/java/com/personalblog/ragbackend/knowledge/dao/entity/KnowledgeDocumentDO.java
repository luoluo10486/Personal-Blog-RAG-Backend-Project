package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_knowledge_document")
@Data
public class KnowledgeDocumentDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("kb_id")
    private Long kbId;
    @TableField("doc_name")
    private String docName;
    @TableField("source_type")
    private String sourceType;
    @TableField("source_location")
    private String sourceLocation;
    @TableField("source_file_name")
    private String sourceFileName;
    @TableField("enabled")
    private Integer enabled;
    @TableField("schedule_enabled")
    private Integer scheduleEnabled;
    @TableField("schedule_cron")
    private String scheduleCron;
    @TableField("status")
    private String status;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("file_url")
    private String fileUrl;
    @TableField("file_type")
    private String fileType;
    @TableField("file_size")
    private Long fileSize;
    @TableField("process_mode")
    private String processMode;
    @TableField("chunk_strategy")
    private String chunkStrategy;
    @TableField("chunk_config")
    private String chunkConfig;
    @TableField("pipeline_id")
    private Long pipelineId;
    @TableField("error_message")
    private String errorMessage;
    @TableField("metadata_json")
    private String metadataJson;
    @TableField("created_by")
    private Long createdBy;
    @TableField("updated_by")
    private Long updatedBy;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
