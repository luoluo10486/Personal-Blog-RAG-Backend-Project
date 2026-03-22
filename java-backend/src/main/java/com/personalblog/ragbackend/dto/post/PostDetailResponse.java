package com.personalblog.ragbackend.dto.post;

import java.util.List;

/**
 * PostDetailResponse 数据传输对象，用于接口参数与返回值封装。
 */
public record PostDetailResponse(
        long id,
        String slug,
        String title,
        List<String> tags,
        String content
) {
}

