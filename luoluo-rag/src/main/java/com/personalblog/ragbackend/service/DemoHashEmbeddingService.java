package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.rag.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提供一个离线可运行的哈希向量化实现，便于本地演示 Milvus 全流程。
 */
@Service
public class DemoHashEmbeddingService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final RagProperties ragProperties;

    public DemoHashEmbeddingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<double[]> embed(List<String> texts) {
        List<double[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    public double[] embed(String text) {
        int dimension = ragProperties.getDemoEmbeddingDimension();
        double[] vector = new double[dimension];
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        int tokenCount = 0;

        while (matcher.find()) {
            String token = matcher.group();
            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimension);
            double sign = (hash & 1) == 0 ? 1D : -1D;
            vector[index] += sign * Math.max(1, token.length()) / 8D;
            tokenCount++;
        }

        if (tokenCount == 0) {
            return vector;
        }

        normalize(vector);
        return vector;
    }

    public String modelName() {
        return "demo-hash-embedding-v1";
    }

    private void normalize(double[] vector) {
        double sum = 0D;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0D) {
            return;
        }

        double norm = Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
    }
}
