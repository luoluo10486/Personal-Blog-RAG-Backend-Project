package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionPlan;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.springframework.stereotype.Component;

@Component
public class PlanIngestionNode implements KnowledgeIngestionNode {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;

    public PlanIngestionNode(KnowledgeProperties knowledgeProperties,
                             KnowledgeVectorSpaceResolver vectorSpaceResolver) {
        this.knowledgeProperties = knowledgeProperties;
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
    public void execute(KnowledgeIngestionContext context) {
        String normalizedBaseCode = vectorSpaceResolver.normalizeBaseCode(context.getBaseCode());
        context.setPlan(new KnowledgeIngestionPlan(
                normalizedBaseCode,
                vectorSpaceResolver.resolve(normalizedBaseCode),
                buildChunkingOptions()
        ));
    }

    private TextChunkingOptions buildChunkingOptions() {
        int targetChunkSize = Math.max(1, knowledgeProperties.getChunking().getChunkSize());
        int overlapSize = Math.max(0, knowledgeProperties.getChunking().getChunkOverlap());
        int maxChunkSize = Math.max(targetChunkSize, targetChunkSize + Math.max(overlapSize, 300));
        int maxChunkCount = Math.max(1, knowledgeProperties.getChunking().getMaxChunkCount());
        return new TextChunkingOptions(targetChunkSize, maxChunkSize, overlapSize, maxChunkCount);
    }
}
