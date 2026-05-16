package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_knowledge_chunk")
@Data
public class KnowledgeChunkDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("kb_id")
    private Long kbId;
    @TableField("doc_id")
    private Long docId;
    @TableField("chunk_index")
    private Integer chunkIndex;
    @TableField("content")
    private String content;
    @TableField("content_hash")
    private String contentHash;
    @TableField("char_count")
    private Integer charCount;
    @TableField("token_count")
    private Integer tokenCount;
    @TableField("enabled")
    private Integer enabled;
    @TableField("vector_id")
    private String vectorId;
    @TableField("metadata_json")
    private String metadata;
    @TableField("created_by")
    private Long createdBy;
    @TableField("updated_by")
    private Long updatedBy;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
