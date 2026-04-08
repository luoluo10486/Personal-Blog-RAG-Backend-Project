package com.personalblog.ragbackend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 BM25 评分器：用于 demo 模式下的稀疏召回与重排兜底。
 */
final class SimpleBm25Scorer {
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<Map<String, Integer>> termFrequencies;
    private final Map<String, Integer> documentFrequencies;
    private final List<Integer> documentLengths;
    private final double averageDocumentLength;

    SimpleBm25Scorer(List<String> documents) {
        this.termFrequencies = new ArrayList<>(documents.size());
        this.documentFrequencies = new HashMap<>();
        this.documentLengths = new ArrayList<>(documents.size());

        int totalLength = 0;
        for (String document : documents) {
            List<String> tokens = TextTokenUtils.tokenize(document);
            Map<String, Integer> frequency = new HashMap<>();
            for (String token : tokens) {
                frequency.merge(token, 1, Integer::sum);
            }
            this.termFrequencies.add(frequency);
            this.documentLengths.add(tokens.size());
            totalLength += tokens.size();

            for (String token : frequency.keySet()) {
                this.documentFrequencies.merge(token, 1, Integer::sum);
            }
        }
        this.averageDocumentLength = documents.isEmpty() ? 0 : totalLength / (double) documents.size();
    }

    List<Double> scoreAll(String query) {
        List<String> queryTokens = TextTokenUtils.tokenize(query);
        List<Double> scores = new ArrayList<>(termFrequencies.size());
        for (int index = 0; index < termFrequencies.size(); index++) {
            scores.add(score(queryTokens, index));
        }
        return scores;
    }

    private double score(List<String> queryTokens, int documentIndex) {
        if (queryTokens.isEmpty()) {
            return 0;
        }

        Map<String, Integer> frequency = termFrequencies.get(documentIndex);
        int documentLength = documentLengths.get(documentIndex);
        double denominatorFactor = 1 - B + B * (documentLength / Math.max(averageDocumentLength, 1.0));
        double score = 0;

        for (String token : queryTokens) {
            int tf = frequency.getOrDefault(token, 0);
            if (tf == 0) {
                continue;
            }
            int df = documentFrequencies.getOrDefault(token, 0);
            double idf = Math.log1p((termFrequencies.size() - df + 0.5) / (df + 0.5));
            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * denominatorFactor;
            score += idf * (numerator / denominator);
        }
        return score;
    }
}
