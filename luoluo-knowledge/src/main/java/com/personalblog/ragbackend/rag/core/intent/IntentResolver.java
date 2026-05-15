package com.personalblog.ragbackend.rag.core.intent;

import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IntentResolver {
    private final RagIntentResolver delegate;

    public IntentResolver(RagIntentResolver delegate) {
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
