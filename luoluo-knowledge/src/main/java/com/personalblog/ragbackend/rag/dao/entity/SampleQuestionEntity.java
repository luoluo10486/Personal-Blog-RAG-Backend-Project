package com.personalblog.ragbackend.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("t_sample_question")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleQuestionEntity {
    @TableId("id")
    public String id;
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
    @TableField("create_time")
    public LocalDateTime createdAt;
    @TableField("update_time")
    public LocalDateTime updatedAt;
}

