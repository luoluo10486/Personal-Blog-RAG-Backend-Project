package com.personalblog.ragbackend.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_ingestion_pipeline_node")
public class IngestionPipelineNodeEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("pipeline_id")
    public Long pipelineId;
    @TableField("node_id")
    public String nodeId;
    @TableField("node_type")
    public String nodeType;
    @TableField("next_node_id")
    public String nextNodeId;
    @TableField("settings_json")
    public String settingsJson;
    @TableField("condition_json")
    public String conditionJson;
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
