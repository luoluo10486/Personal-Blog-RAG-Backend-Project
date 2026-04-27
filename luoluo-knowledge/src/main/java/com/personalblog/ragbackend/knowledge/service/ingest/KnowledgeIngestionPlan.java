package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;

public record KnowledgeIngestionPlan(
        String baseCode,
        KnowledgeVectorSpace vectorSpace,
        int chunkSize,
        int chunkOverlap,
        int maxChunkCount
) {
}
