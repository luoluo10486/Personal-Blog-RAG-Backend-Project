package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.postprocessor.SearchResultPostProcessor;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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

    @RagTraceNode(name = "retrieval-engine", type = "RETRIEVE")
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        List<RetrievedChunk> processed = retrieveCandidates(request);
        for (SearchResultPostProcessor processor : postProcessors) {
            if (!processor.isEnabled(request)) {
                continue;
            }
            processed = processor.process(processed, request);
        }
        return processed.stream().limit(request.topK()).toList();
    }

    private List<RetrievedChunk> retrieveCandidates(RetrieveRequest request) {
        if (candidateRetrievers.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> merged = new ArrayList<>();
        int enabledCount = 0;
        for (KnowledgeCandidateRetriever retriever : candidateRetrievers) {
            if (!retriever.isEnabled(request)) {
                continue;
            }
            enabledCount++;
            try {
                List<RetrievedChunk> candidates = retriever.retrieveCandidates(request);
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
                .sorted(Comparator.comparingDouble((RetrievedChunk chunk) -> chunk.getScore() == null ? 0D : chunk.getScore()).reversed())
                .toList();
    }
}
