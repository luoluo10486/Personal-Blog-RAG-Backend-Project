package com.personalblog.ragbackend.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.rag.config.GuidanceProperties;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.NodeScoreFilters;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IntentGuidanceService {
    private final GuidanceProperties guidanceProperties;
    private final PromptTemplateLoader promptTemplateLoader;
    private final AmbiguityLLMChecker ambiguityLLMChecker;

    public IntentGuidanceService(GuidanceProperties guidanceProperties,
                                 PromptTemplateLoader promptTemplateLoader,
                                 AmbiguityLLMChecker ambiguityLLMChecker) {
        this.guidanceProperties = guidanceProperties;
        this.promptTemplateLoader = promptTemplateLoader;
        this.ambiguityLLMChecker = ambiguityLLMChecker;
    }

    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (!Boolean.TRUE.equals(guidanceProperties.getEnabled())) {
            return GuidanceDecision.none();
        }

        AmbiguityGroup group = findAmbiguityGroup(question, subIntents);
        if (group == null || CollUtil.isEmpty(group.ranked())) {
            return GuidanceDecision.none();
        }

        return GuidanceDecision.prompt(buildPrompt(group.topicName(), group.ranked()));
    }

    private AmbiguityGroup findAmbiguityGroup(String question, List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return null;
        }
        List<NodeScore> candidates = filterCandidates(subIntents.get(0).nodeScores());
        if (candidates.size() < 2) {
            return null;
        }

        Map<String, NodeScore> systemBest = candidates.stream()
                .filter(ns -> StrUtil.isNotBlank(resolveSystemNodeId(ns.node())))
                .collect(Collectors.toMap(
                        ns -> resolveSystemNodeId(ns.node()),
                        ns -> ns,
                        (a, b) -> a.score() >= b.score() ? a : b
                ));

        List<NodeScore> ranked = systemBest.values().stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .toList();
        if (ranked.size() < 2) {
            return null;
        }
        if (shouldSkipGuidance(question, ranked)) {
            return null;
        }
        if (!confirmAmbiguity(question, ranked)) {
            return null;
        }
        List<NodeScore> trimmedRanked = trimRankedOptions(ranked);
        String topicName = trimmedRanked.get(0).node().getName();
        return new AmbiguityGroup(topicName, trimmedRanked);
    }

    private boolean shouldSkipGuidance(String question, List<NodeScore> ranked) {
        double top = ranked.get(0).score();
        if (top <= 0) {
            return true;
        }
        double ratio = ranked.get(1).score() / top;
        double threshold = Optional.ofNullable(guidanceProperties.getAmbiguityScoreRatio()).orElse(0.8D);
        double margin = Optional.ofNullable(guidanceProperties.getAmbiguityMargin()).orElse(0.15D);
        if (ratio < threshold - margin) {
            return true;
        }
        if (StrUtil.isNotBlank(question)) {
            List<String> domainNames = ranked.stream()
                    .map(ns -> resolveDomainName(ns.node()))
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .toList();
            String normalizedQuestion = normalizeName(question);
            for (String name : domainNames) {
                if (normalizedQuestion.contains(normalizeName(name))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean confirmAmbiguity(String question, List<NodeScore> ranked) {
        double top = ranked.get(0).score();
        double second = ranked.get(1).score();
        if (top <= 0) {
            return false;
        }
        double ratio = second / top;
        double threshold = guidanceProperties.getAmbiguityScoreRatio();
        double margin = guidanceProperties.getAmbiguityMargin();
        if (ratio >= threshold) {
            return true;
        }
        if (ratio >= threshold - margin) {
            return ambiguityLLMChecker.checkAmbiguity(question, ranked);
        }
        return false;
    }

    private List<NodeScore> filterCandidates(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return NodeScoreFilters.kb(scores, RAGConstant.INTENT_MIN_SCORE);
    }

    private String resolveDomainName(IntentNode node) {
        return node == null ? "" : StrUtil.blankToDefault(node.getName(), "");
    }

    private String resolveSystemNodeId(IntentNode node) {
        if (node == null) {
            return "";
        }
        return StrUtil.blankToDefault(node.getId(), "");
    }

    private List<NodeScore> trimRankedOptions(List<NodeScore> ranked) {
        int maxOptions = Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(ranked.size());
        if (ranked.size() <= maxOptions) {
            return ranked;
        }
        return ranked.subList(0, maxOptions);
    }

    private String buildPrompt(String topicName, List<NodeScore> ranked) {
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            IntentNode node = ranked.get(i).node();
            options.append(i + 1).append(") ").append(node == null ? "" : StrUtil.blankToDefault(node.getFullPath(), node.getName())).append("\n");
        }
        return promptTemplateLoader.render(
                RAGConstant.GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, ""),
                        "options", options.toString().trim()
                )
        );
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
    }

    private record AmbiguityGroup(String topicName, List<NodeScore> ranked) {
    }
}
