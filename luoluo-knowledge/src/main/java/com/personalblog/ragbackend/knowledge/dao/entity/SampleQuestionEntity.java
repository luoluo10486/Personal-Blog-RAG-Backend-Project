package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_sample_question")
public class SampleQuestionEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("kb_id")
    public Long kbId;
    @TableField("title")
    public String title;
    @TableField("description")
    public String description;
    @TableField("question")
    public String question;
    @TableField("sort_order")
    public Integer sortOrder;
    @TableField("enabled")
    public Integer enabled;
    @TableLogic
    @TableField("deleted")
    public Integer deleted;
    @TableField("created_at")
    public LocalDateTime createdAt;
    @TableField("updated_at")
    public LocalDateTime updatedAt;
}
