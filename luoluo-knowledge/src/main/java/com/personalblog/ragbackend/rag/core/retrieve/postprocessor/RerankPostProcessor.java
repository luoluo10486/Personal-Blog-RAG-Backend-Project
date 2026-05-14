package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RerankPostProcessor implements SearchResultPostProcessor {
    private final KnowledgeProperties knowledgeProperties;
    private final ObjectProvider<RerankService> rerankServiceProvider;

    public RerankPostProcessor(KnowledgeProperties knowledgeProperties,
                               ObjectProvider<RerankService> rerankServiceProvider) {
        this.knowledgeProperties = knowledgeProperties;
        this.rerankServiceProvider = rerankServiceProvider;
    }

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public boolean isEnabled(RetrieveRequest request) {
        return knowledgeProperties.getSearch().getRerank().isEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, RetrieveRequest request) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        RerankService rerankService = rerankServiceProvider.getIfAvailable();
        if (rerankService == null) {
            return chunks.stream().limit(request.topK()).toList();
        }

        try {
            Map<String, RetrievedChunk> byId = new LinkedHashMap<>();
            List<RetrievedChunk> candidates = chunks.stream()
                    .peek(chunk -> byId.put(chunk.getId(), chunk))
                    .toList();

            return rerankService.rerank(request.question(), candidates, request.topK());
        } catch (RuntimeException ex) {
            return chunks.stream().limit(request.topK()).toList();
        }
    }
}
