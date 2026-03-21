package com.personalblog.ragbackend.model;

import java.util.List;

/**
 * Post 领域模型，描述业务数据结构。
 */
public record Post(
        long id,
        String slug,
        String title,
        List<String> tags,
        String content
) {
}

