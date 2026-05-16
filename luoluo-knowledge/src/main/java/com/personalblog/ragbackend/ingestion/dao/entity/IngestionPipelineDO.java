package com.personalblog.ragbackend.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_ingestion_pipeline")
@Data
public class IngestionPipelineDO {
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
    @TableField("create_time")
    public LocalDateTime createdAt;
    @TableField("update_time")
    public LocalDateTime updatedAt;
}
