package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_query_term_mapping")
public class QueryTermMappingEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("domain")
    public String domain;
    @TableField("source_term")
    public String sourceTerm;
    @TableField("target_term")
    public String targetTerm;
    @TableField("match_type")
    public Integer matchType;
    @TableField("priority")
    public Integer priority;
    @TableField("enabled")
    public Integer enabled;
    @TableField("remark")
    public String remark;
    @TableLogic
    @TableField("deleted")
    public Integer deleted;
    @TableField("created_at")
    public LocalDateTime createdAt;
    @TableField("updated_at")
    public LocalDateTime updatedAt;
}
