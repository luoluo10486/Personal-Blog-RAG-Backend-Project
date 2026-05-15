package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionPlan;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Component;

@Component
public class PlanIngestionNode implements KnowledgeIngestionNode {
    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 128;
    private static final int DEFAULT_MAX_CHUNK_COUNT = 1000;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;

    public PlanIngestionNode(KnowledgeVectorSpaceResolver vectorSpaceResolver) {
        this.vectorSpaceResolver = vectorSpaceResolver;
    }

    @Override
    public String getNodeType() {
        return "plan";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    @RagTraceNode(name = "plan-ingestion", type = "INGEST_PLAN")
    public void execute(KnowledgeIngestionContext context) {
        String normalizedBaseCode = vectorSpaceResolver.normalizeBaseCode(context.getBaseCode());
        context.setPlan(new KnowledgeIngestionPlan(
                normalizedBaseCode,
                vectorSpaceResolver.resolve(normalizedBaseCode),
                buildChunkingOptions()
        ));
    }

    private TextChunkingOptions buildChunkingOptions() {
        int targetChunkSize = DEFAULT_CHUNK_SIZE;
        int overlapSize = DEFAULT_CHUNK_OVERLAP;
        int maxChunkSize = Math.max(targetChunkSize, targetChunkSize + Math.max(overlapSize, 300));
        int maxChunkCount = DEFAULT_MAX_CHUNK_COUNT;
        return new TextChunkingOptions(targetChunkSize, maxChunkSize, overlapSize, maxChunkCount);
    }
}
