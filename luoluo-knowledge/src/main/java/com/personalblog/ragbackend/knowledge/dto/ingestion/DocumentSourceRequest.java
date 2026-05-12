package com.personalblog.ragbackend.knowledge.dto.ingestion;

import java.util.Map;

public record DocumentSourceRequest(
        String type,
        String location,
        String fileName,
        Map<String, String> credentials
) {
}
