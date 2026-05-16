package com.personalblog.ragbackend.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("t_knowledge_vector_ref")
@Data
public class KnowledgeVectorRefDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("kb_id")
    private Long kbId;
    @TableField("doc_id")
    private Long docId;
    @TableField("chunk_id")
    private Long chunkId;
    @TableField("vector_id")
    private String vectorId;
    @TableField("collection_name")
    private String collectionName;
    @TableField("embedding_model")
    private String embeddingModel;
    @TableField("embedding_dim")
    private Integer embeddingDim;
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
