package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;

public record KnowledgeIngestionResult(
        KnowledgeIngestionPlan plan,
        ParseResult parseResult,
        DocumentChunkResponse chunkResponse
) {
}
