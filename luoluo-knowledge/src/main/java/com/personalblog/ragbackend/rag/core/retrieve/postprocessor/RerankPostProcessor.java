package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
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
        return 10;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, List<SearchChannelResult> results, SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return chunks;
        }
        RerankService rerankService = rerankServiceProvider.getIfAvailable();
        if (rerankService == null) {
            return chunks.stream().limit(resolveTopK(context)).toList();
        }
        try {
            String question = context == null ? "" : context.getMainQuestion();
            return rerankService.rerank(question, chunks, resolveTopK(context));
        } catch (RuntimeException ex) {
            return chunks.stream().limit(resolveTopK(context)).toList();
        }
    }

    private int resolveTopK(SearchContext context) {
        if (context == null || context.getTopK() <= 0) {
            return 1;
        }
        return context.getTopK();
    }
}
