package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfidenceThresholdPostProcessor implements SearchResultPostProcessor {
    private final KnowledgeProperties knowledgeProperties;

    public ConfidenceThresholdPostProcessor(KnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    @Override
    public String getName() {
        return "ConfidenceThreshold";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, RetrieveRequest request) {
        List<RetrievedChunk> filtered = chunks.stream()
                .filter(chunk -> chunk.getScore() != null && chunk.getScore() >= knowledgeProperties.getSearch().getConfidenceThreshold())
                .toList();
        if (filtered.isEmpty()) {
            return chunks.stream().limit(request.topK()).toList();
        }
        return filtered;
    }
}
