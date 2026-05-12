package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_knowledge_document_chunk_log")
public class KnowledgeDocumentChunkLogEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("doc_id")
    private Long docId;
    @TableField("status")
    private String status;
    @TableField("process_mode")
    private String processMode;
    @TableField("chunk_strategy")
    private String chunkStrategy;
    @TableField("pipeline_id")
    private Long pipelineId;
    @TableField("extract_duration")
    private Long extractDuration;
    @TableField("chunk_duration")
    private Long chunkDuration;
    @TableField("embed_duration")
    private Long embedDuration;
    @TableField("persist_duration")
    private Long persistDuration;
    @TableField("total_duration")
    private Long totalDuration;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("error_message")
    private String errorMessage;
    @TableField("start_time")
    private LocalDateTime startedAt;
    @TableField("end_time")
    private LocalDateTime endedAt;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProcessMode() { return processMode; }
    public void setProcessMode(String processMode) { this.processMode = processMode; }
    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }
    public Long getPipelineId() { return pipelineId; }
    public void setPipelineId(Long pipelineId) { this.pipelineId = pipelineId; }
    public Long getExtractDuration() { return extractDuration; }
    public void setExtractDuration(Long extractDuration) { this.extractDuration = extractDuration; }
    public Long getChunkDuration() { return chunkDuration; }
    public void setChunkDuration(Long chunkDuration) { this.chunkDuration = chunkDuration; }
    public Long getEmbedDuration() { return embedDuration; }
    public void setEmbedDuration(Long embedDuration) { this.embedDuration = embedDuration; }
    public Long getPersistDuration() { return persistDuration; }
    public void setPersistDuration(Long persistDuration) { this.persistDuration = persistDuration; }
    public Long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(Long totalDuration) { this.totalDuration = totalDuration; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
