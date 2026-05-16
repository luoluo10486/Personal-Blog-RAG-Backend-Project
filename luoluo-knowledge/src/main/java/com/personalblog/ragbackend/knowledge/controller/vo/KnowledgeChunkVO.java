package com.personalblog.ragbackend.knowledge.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Knowledge chunk view object.
 */
@Data
public class KnowledgeChunkVO {
    private String id;
    private String kbId;
    private String docId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer charCount;
    private Integer tokenCount;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
