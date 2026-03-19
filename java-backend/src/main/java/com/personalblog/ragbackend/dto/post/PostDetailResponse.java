package com.personalblog.ragbackend.dto.post;

import java.util.List;

public record PostDetailResponse(
        long id,
        String slug,
        String title,
        List<String> tags,
        String content
) {
}
