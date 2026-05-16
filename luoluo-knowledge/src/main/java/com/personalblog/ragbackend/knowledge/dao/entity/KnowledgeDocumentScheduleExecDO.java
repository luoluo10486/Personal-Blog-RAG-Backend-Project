package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_knowledge_document_schedule_exec")
@Data
public class KnowledgeDocumentScheduleExecDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("schedule_id")
    private Long scheduleId;
    @TableField("doc_id")
    private Long docId;
    @TableField("status")
    private String status;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("completed_at")
    private LocalDateTime endedAt;
    @TableField("message")
    private String message;
    @TableField("error_message")
    private String errorMessage;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
