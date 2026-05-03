package com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.rerank.RerankService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
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
    public List<KnowledgeChunk> process(List<KnowledgeChunk> chunks, RetrieveRequest request) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        RerankService rerankService = rerankServiceProvider.getIfAvailable();
        if (rerankService == null) {
            return chunks.stream().limit(request.topK()).toList();
        }

        try {
            Map<String, KnowledgeChunk> byId = new LinkedHashMap<>();
            List<RetrievedChunk> candidates = chunks.stream()
                    .peek(chunk -> byId.put(chunk.id(), chunk))
                    .map(chunk -> new RetrievedChunk(chunk.id(), chunk.content(), (float) chunk.score()))
                    .toList();

            return rerankService.rerank(request.question(), candidates, request.topK()).stream()
                    .map(result -> toKnowledgeChunk(byId.get(result.getId()), result.getScore()))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException ex) {
            return chunks.stream().limit(request.topK()).toList();
        }
    }

    private KnowledgeChunk toKnowledgeChunk(KnowledgeChunk source, Float score) {
        if (source == null) {
            return null;
        }
        return new KnowledgeChunk(
                source.id(),
                source.baseCode(),
                source.documentId(),
                source.title(),
                source.sourceUrl(),
                source.chunkIndex(),
                source.content(),
                score == null ? source.score() : score
        );
    }
}
