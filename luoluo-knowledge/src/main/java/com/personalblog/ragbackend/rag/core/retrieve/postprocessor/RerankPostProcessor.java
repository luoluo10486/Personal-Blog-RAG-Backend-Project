package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RerankPostProcessor implements SearchResultPostProcessor {
    private final ObjectProvider<RerankService> rerankServiceProvider;

    public RerankPostProcessor(ObjectProvider<RerankService> rerankServiceProvider) {
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
        return rerankServiceProvider.getIfAvailable() != null;
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
            return rerankService.rerank(request.question(), chunks, request.topK());
        } catch (RuntimeException ex) {
            return chunks.stream().limit(request.topK()).toList();
        }
    }
}
