package com.personalblog.ragbackend.service;

/**
 * 余弦相似度计算工具。
 */
public final class CosineSimilarity {
    private CosineSimilarity() {
    }

    public static double calculate(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("vector dimensions do not match");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int index = 0; index < vectorA.length; index++) {
            dotProduct += vectorA[index] * vectorB[index];
            normA += vectorA[index] * vectorA[index];
            normB += vectorB[index] * vectorB[index];
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (normA * normB);
    }
}
