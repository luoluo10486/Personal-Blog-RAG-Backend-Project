package com.personalblog.ragbackend.knowledge.dto.ingestion;

import com.personalblog.ragbackend.knowledge.domain.enums.SourceType;

import java.util.Map;

public record DocumentSourceRequest(
        SourceType type,
        String location,
        String fileName,
        Map<String, String> credentials
) {
}
