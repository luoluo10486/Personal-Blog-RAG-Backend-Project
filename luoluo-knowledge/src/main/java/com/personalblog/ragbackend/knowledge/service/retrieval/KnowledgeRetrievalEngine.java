package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor.SearchResultPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeRetrievalEngine {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalEngine.class);

    private final List<KnowledgeCandidateRetriever> candidateRetrievers;
    private final List<SearchResultPostProcessor> postProcessors;

    public KnowledgeRetrievalEngine(List<KnowledgeCandidateRetriever> candidateRetrievers,
                                    List<SearchResultPostProcessor> postProcessors) {
        this.candidateRetrievers = candidateRetrievers.stream()
                .sorted(Comparator.comparingInt(KnowledgeCandidateRetriever::getOrder))
                .toList();
        this.postProcessors = postProcessors.stream()
                .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder))
                .toList();
    }

    public List<KnowledgeChunk> retrieve(RetrieveRequest request) {
        List<KnowledgeChunk> processed = retrieveCandidates(request);
        for (SearchResultPostProcessor processor : postProcessors) {
            if (!processor.isEnabled(request)) {
                continue;
            }
            processed = processor.process(processed, request);
        }
        return processed.stream().limit(request.topK()).toList();
    }

    private List<KnowledgeChunk> retrieveCandidates(RetrieveRequest request) {
        if (candidateRetrievers.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunk> merged = new ArrayList<>();
        int enabledCount = 0;
        for (KnowledgeCandidateRetriever retriever : candidateRetrievers) {
            if (!retriever.isEnabled(request)) {
                continue;
            }
            enabledCount++;
            try {
                List<KnowledgeChunk> candidates = retriever.retrieveCandidates(request);
                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }
                merged.addAll(candidates);
            } catch (RuntimeException ex) {
                log.warn("Knowledge candidate retriever [{}] failed, skip current retriever", retriever.getName(), ex);
            }
        }

        if (enabledCount == 0) {
            return List.of();
        }

        return merged.stream()
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .toList();
    }
}
