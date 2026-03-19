package com.personalblog.ragbackend.dto.post;

import java.util.List;

public record PostSummaryResponse(
        long id,
        String slug,
        String title,
        List<String> tags
) {
}
