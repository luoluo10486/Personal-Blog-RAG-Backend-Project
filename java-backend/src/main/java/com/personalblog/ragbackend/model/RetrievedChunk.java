package com.personalblog.ragbackend.model;

/**
 * RetrievedChunk 领域模型，描述业务数据结构。
 */
public record RetrievedChunk(
        String id,
        String title,
        String content,
        double score
) {
}

