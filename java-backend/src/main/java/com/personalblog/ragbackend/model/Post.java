package com.personalblog.ragbackend.model;

import java.util.List;

public record Post(
        long id,
        String slug,
        String title,
        List<String> tags,
        String content
) {
}
