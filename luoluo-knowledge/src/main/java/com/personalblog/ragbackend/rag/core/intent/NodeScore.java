package com.personalblog.ragbackend.rag.core.intent;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeScore {
    private IntentNode node;
    private double score;

    @Builder
    public NodeScore(IntentNode node, double score) {
        this.node = node;
        this.score = score;
    }

    public IntentNode node() {
        return node;
    }

    public double score() {
        return score;
    }
}
