package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RerankPostProcessor implements SearchResultPostProcessor {
    private final RerankService rerankService;

    public RerankPostProcessor(RerankService rerankService) {
        this.rerankService = rerankService;
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
            return List.of();
        }
        try {
            String question = context == null ? "" : context.getMainQuestion();
            return rerankService.rerank(question, chunks, resolveTopK(context));
        } catch (RuntimeException ex) {
            log.warn("Rerank 后置处理失败，回退到截断结果", ex);
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
