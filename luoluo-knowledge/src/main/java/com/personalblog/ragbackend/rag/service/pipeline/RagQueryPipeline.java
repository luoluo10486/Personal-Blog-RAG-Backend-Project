package com.personalblog.ragbackend.rag.service.pipeline;

import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.rag.core.intent.IntentGroup;
import com.personalblog.ragbackend.rag.core.intent.IntentResolver;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.rag.core.rewrite.QueryRewriteService;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagQueryPipeline {
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;

    public RagQueryPipeline(QueryRewriteService queryRewriteService,
                            IntentResolver intentResolver) {
        this.queryRewriteService = queryRewriteService;
        this.intentResolver = intentResolver;
    }

    @RagTraceNode(name = "rag-query-pipeline", type = "PIPELINE")
    public RagQueryPlan prepare(String question, List<ChatMessage> memory) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, memory);

        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);
        IntentGroup intentGroup = intentResolver.mergeIntentGroup(subIntents);
        return new RagQueryPlan(
                question,
                rewriteResult,
                subIntents,
                intentGroup
        );
    }
}
