package com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
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
    public List<KnowledgeChunk> process(List<KnowledgeChunk> chunks, RetrieveRequest request) {
        List<KnowledgeChunk> filtered = chunks.stream()
                .filter(chunk -> chunk.score() >= knowledgeProperties.getSearch().getConfidenceThreshold())
                .toList();
        if (filtered.isEmpty()) {
            return chunks.stream().limit(request.topK()).toList();
        }
        return filtered;
    }
}
