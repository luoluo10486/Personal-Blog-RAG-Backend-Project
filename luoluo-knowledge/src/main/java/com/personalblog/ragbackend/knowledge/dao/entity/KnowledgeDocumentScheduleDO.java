package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_knowledge_document_schedule")
@Data
public class KnowledgeDocumentScheduleDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("doc_id")
    private Long docId;
    @TableField("kb_id")
    private Long kbId;
    @TableField("cron_expr")
    private String cronExpr;
    @TableField("enabled")
    private Integer enabled;
    @TableField("next_run_at")
    private LocalDateTime nextRunTime;
    @TableField("lock_owner")
    private String lockOwner;
    @TableField("lock_until")
    private LocalDateTime lockUntil;
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
