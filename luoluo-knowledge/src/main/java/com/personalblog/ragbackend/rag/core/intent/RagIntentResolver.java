package com.personalblog.ragbackend.rag.core.intent;

import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.Executor;

@Deprecated(forRemoval = false)
public class RagIntentResolver extends IntentResolver {
    public RagIntentResolver(@Qualifier("defaultIntentClassifier") IntentClassifier intentClassifier,
                             @Qualifier("intentClassifyThreadPoolExecutor") Executor intentClassifyExecutor) {
        super(intentClassifier, intentClassifyExecutor);
    }
}
