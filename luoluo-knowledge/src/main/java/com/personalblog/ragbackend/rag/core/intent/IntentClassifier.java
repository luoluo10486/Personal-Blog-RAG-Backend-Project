package com.personalblog.ragbackend.rag.core.intent;

import java.util.List;

public interface IntentClassifier {
    List<NodeScore> classifyTargets(String question);

    default List<NodeScore> topKAboveThreshold(String question, int topN, double minScore) {
        return classifyTargets(question).stream()
                .filter(ns -> ns.score() >= minScore)
                .limit(topN)
                .toList();
    }
}
