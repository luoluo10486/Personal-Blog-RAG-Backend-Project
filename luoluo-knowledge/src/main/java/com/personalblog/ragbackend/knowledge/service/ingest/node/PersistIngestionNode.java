package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionPersistenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class PersistIngestionNode implements KnowledgeIngestionNode {
    private final ObjectProvider<KnowledgeIngestionPersistenceService> persistenceServiceProvider;

    public PersistIngestionNode(ObjectProvider<KnowledgeIngestionPersistenceService> persistenceServiceProvider) {
        this.persistenceServiceProvider = persistenceServiceProvider;
    }

    @Override
    public String getNodeType() {
        return "persist";
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public void execute(KnowledgeIngestionContext context) {
        if (!context.isIngestMode()) {
            return;
        }
        if (context.getParseResult() == null || !context.getParseResult().success()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Document parsing failed"));
            return;
        }
        if (context.getChunkResponse() == null || !context.getChunkResponse().success()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Document chunking failed"));
            return;
        }

        KnowledgeIngestionPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService == null) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Knowledge persistence service is unavailable"));
            return;
        }
        persistenceService.persist(context);
    }
}
