package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import org.springframework.stereotype.Component;

@Component
public class FinalizeIngestionNode implements KnowledgeIngestionNode {

    @Override
    public String getNodeType() {
        return "finalize";
    }

    @Override
    public int getOrder() {
        return 70;
    }

    @Override
    public void execute(KnowledgeIngestionContext context) {
        if (!context.isIngestMode()) {
            return;
        }
        if (context.getIngestionSummary() != null) {
            return;
        }
        if (context.getPlan() == null) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Ingestion plan is missing"));
            return;
        }
        if (context.getParseResult() == null || !context.getParseResult().success()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getPlan().baseCode(), "Document parsing failed"));
            return;
        }
        if (context.getChunkResponse() == null || !context.getChunkResponse().success()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getPlan().baseCode(), "Document chunking failed"));
            return;
        }
        if (!context.isVectorIndexed()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getPlan().baseCode(), "Vector indexing did not complete"));
            return;
        }
        context.setIngestionSummary(DocumentIngestionSummary.success(
                context.getPlan().baseCode(),
                context.getPlan().vectorSpace().collectionName(),
                context.getPlan().vectorSpace().embeddingModel(),
                context.getKnowledgeBaseId(),
                context.getDocumentId(),
                context.getPersistedChunks().size(),
                context.getEmbeddings().size(),
                true
        ));
    }
}
