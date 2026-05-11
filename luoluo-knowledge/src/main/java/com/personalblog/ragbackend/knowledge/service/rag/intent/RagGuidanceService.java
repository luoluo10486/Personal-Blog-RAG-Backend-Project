package com.personalblog.ragbackend.knowledge.service.rag.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RagGuidanceService {
    private static final double AMBIGUITY_RATIO = 0.85D;

    @RagTraceNode(name = "intent-guidance", type = "GUIDANCE")
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return GuidanceDecision.none();
        }
        List<NodeScore> candidates = subIntents.get(0).nodeScores();
        if (CollUtil.isEmpty(candidates) || candidates.size() < 2) {
            return GuidanceDecision.none();
        }

        List<NodeScore> ranked = candidates.stream()
                .filter(score -> score != null && score.node() != null)
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .toList();
        if (ranked.size() < 2) {
            return GuidanceDecision.none();
        }

        double top = ranked.get(0).score();
        double second = ranked.get(1).score();
        if (top <= 0 || second / top < AMBIGUITY_RATIO) {
            return GuidanceDecision.none();
        }

        List<NodeScore> options = ranked.stream()
                .limit(3)
                .toList();
        StringBuilder prompt = new StringBuilder("I need a bit more detail to route this correctly. Which one did you mean?\n");
        for (int i = 0; i < options.size(); i++) {
            RagIntentNode node = options.get(i).node();
            prompt.append(i + 1).append(". ")
                    .append(StrUtil.blankToDefault(node.fullPath, node.name))
                    .append("\n");
        }
        return GuidanceDecision.prompt(prompt.toString().trim());
    }
}
