package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("t_intent_node")
public class IntentNodeEntity {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("kb_id")
    public Long kbId;
    @TableField("intent_code")
    public String intentCode;
    @TableField("name")
    public String name;
    @TableField("level")
    public Integer level;
    @TableField("parent_code")
    public String parentCode;
    @TableField("description")
    public String description;
    @TableField("examples")
    public String examples;
    @TableField("collection_name")
    public String collectionName;
    @TableField("top_k")
    public Integer topK;
    @TableField("mcp_tool_id")
    public String mcpToolId;
    @TableField("kind")
    public Integer kind;
    @TableField("prompt_snippet")
    public String promptSnippet;
    @TableField("prompt_template")
    public String promptTemplate;
    @TableField("param_prompt_template")
    public String paramPromptTemplate;
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
