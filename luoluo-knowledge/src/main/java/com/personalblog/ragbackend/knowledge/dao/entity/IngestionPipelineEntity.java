package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_ingestion_pipeline")
public class IngestionPipelineEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("name")
    public String name;
    @TableField("description")
    public String description;
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
