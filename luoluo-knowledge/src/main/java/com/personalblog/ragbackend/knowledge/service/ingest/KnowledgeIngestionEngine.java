package com.personalblog.ragbackend.knowledge.service.ingest;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class KnowledgeIngestionEngine {
    private final List<KnowledgeIngestionNode> nodes;

    public KnowledgeIngestionEngine(List<KnowledgeIngestionNode> nodes) {
        this.nodes = nodes.stream()
                .sorted(Comparator.comparingInt(KnowledgeIngestionNode::getOrder))
                .toList();
    }

    public KnowledgeIngestionResult execute(KnowledgeIngestionRequest request) {
        KnowledgeIngestionContext context = new KnowledgeIngestionContext(
                request.baseCode(),
                request.file(),
                request.mode()
        );
        for (KnowledgeIngestionNode node : nodes) {
            node.execute(context);
        }
        return new KnowledgeIngestionResult(
                context.getPlan(),
                context.getParseResult(),
                context.getChunkResponse(),
                context.getIngestionSummary()
        );
    }
}
