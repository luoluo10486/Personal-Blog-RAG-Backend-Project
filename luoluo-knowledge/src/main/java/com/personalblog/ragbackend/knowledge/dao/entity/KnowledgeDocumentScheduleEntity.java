package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_knowledge_document_schedule")
public class KnowledgeDocumentScheduleEntity {
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

    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    @TableField("last_run_time")
    private LocalDateTime lastRunTime;

    @TableField("last_success_time")
    private LocalDateTime lastSuccessTime;

    @TableField("last_status")
    private String lastStatus;

    @TableField("last_error")
    private String lastError;

    @TableField("last_etag")
    private String lastEtag;

    @TableField("last_modified")
    private String lastModified;

    @TableField("last_content_hash")
    private String lastContentHash;

    @TableField("lock_owner")
    private String lockOwner;

    @TableField("lock_until")
    private LocalDateTime lockUntil;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(LocalDateTime nextRunTime) { this.nextRunTime = nextRunTime; }
    public LocalDateTime getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(LocalDateTime lastRunTime) { this.lastRunTime = lastRunTime; }
    public LocalDateTime getLastSuccessTime() { return lastSuccessTime; }
    public void setLastSuccessTime(LocalDateTime lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getLastEtag() { return lastEtag; }
    public void setLastEtag(String lastEtag) { this.lastEtag = lastEtag; }
    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    public String getLastContentHash() { return lastContentHash; }
    public void setLastContentHash(String lastContentHash) { this.lastContentHash = lastContentHash; }
    public String getLockOwner() { return lockOwner; }
    public void setLockOwner(String lockOwner) { this.lockOwner = lockOwner; }
    public LocalDateTime getLockUntil() { return lockUntil; }
    public void setLockUntil(LocalDateTime lockUntil) { this.lockUntil = lockUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}


