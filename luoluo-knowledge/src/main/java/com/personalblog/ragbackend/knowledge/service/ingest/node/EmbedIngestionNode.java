package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.infra.ai.embedding.EmbeddingService;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbedIngestionNode implements KnowledgeIngestionNode {
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;

    public EmbedIngestionNode(ObjectProvider<EmbeddingService> embeddingServiceProvider) {
        this.embeddingServiceProvider = embeddingServiceProvider;
    }

    @Override
    public String getNodeType() {
        return "embed";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public void execute(KnowledgeIngestionContext context) {
        if (!context.isIngestMode()) {
            return;
        }
        if (context.getIngestionSummary() != null && !context.getIngestionSummary().success()) {
            return;
        }
        if (context.getPersistedChunks().isEmpty() || context.getChunks().isEmpty()) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "No persisted chunks are available for embedding"));
            return;
        }

        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        if (embeddingService == null) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(context.getBaseCode(), "Embedding service is unavailable"));
            return;
        }

        try {
            List<List<Float>> embeddings = embeddingService.embedBatch(
                    context.getChunks().stream().map(chunk -> chunk.content()).toList()
            );
            context.setEmbeddings(embeddings);
        } catch (RuntimeException exception) {
            context.setIngestionSummary(DocumentIngestionSummary.failure(
                    context.getBaseCode(),
                    "Embedding generation failed: " + exception.getMessage()
            ));
        }
    }
}
