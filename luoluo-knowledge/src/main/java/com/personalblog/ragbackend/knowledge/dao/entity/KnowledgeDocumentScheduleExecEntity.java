package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_knowledge_document_schedule_exec")
public class KnowledgeDocumentScheduleExecEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("schedule_id")
    private Long scheduleId;

    @TableField("doc_id")
    private Long docId;

    @TableField("kb_id")
    private Long kbId;

    @TableField("status")
    private String status;

    @TableField("message")
    private String message;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("content_hash")
    private String contentHash;

    @TableField("etag")
    private String etag;

    @TableField("last_modified")
    private String lastModified;

    @TableField("create_time")
    private LocalDateTime createdAt;

    @TableField("update_time")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
