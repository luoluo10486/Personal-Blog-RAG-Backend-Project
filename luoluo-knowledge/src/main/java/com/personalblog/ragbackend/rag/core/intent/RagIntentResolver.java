package com.personalblog.ragbackend.rag.core.intent;

import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Legacy compatibility layer for older bean wiring.
 * The real intent resolution flow now lives in {@link IntentResolver}.
 */
@Service
@Deprecated
public class RagIntentResolver {
    private final IntentResolver delegate;

    public RagIntentResolver(IntentResolver delegate) {
        this.delegate = delegate;
    }

    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        return delegate.resolve(rewriteResult);
    }

    public List<SubQuestionIntent> resolve(String question) {
        return delegate.resolve(question);
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        return delegate.mergeIntentGroup(subIntents);
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return delegate.isSystemOnly(nodeScores);
    }
}
