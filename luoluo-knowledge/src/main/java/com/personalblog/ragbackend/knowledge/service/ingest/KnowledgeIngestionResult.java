package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;

public record KnowledgeIngestionResult(
        KnowledgeIngestionPlan plan,
        ParseResult parseResult,
        DocumentChunkResponse chunkResponse,
        java.util.List<java.util.List<Float>> embeddings,
        DocumentIngestionSummary ingestionSummary,
        java.util.List<KnowledgeIngestionNodeLog> nodeLogs
) {
    public KnowledgeIngestionResult(KnowledgeIngestionPlan plan,
                                    ParseResult parseResult,
                                    DocumentChunkResponse chunkResponse,
                                    java.util.List<java.util.List<Float>> embeddings,
                                    DocumentIngestionSummary ingestionSummary) {
        this(plan, parseResult, chunkResponse, embeddings, ingestionSummary, java.util.List.of());
    }
}
