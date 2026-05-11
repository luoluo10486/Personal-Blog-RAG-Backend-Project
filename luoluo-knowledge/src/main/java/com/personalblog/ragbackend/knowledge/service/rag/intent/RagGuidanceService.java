package com.personalblog.ragbackend.knowledge.service.rag.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RagGuidanceService {
    private static final String GUIDANCE_PROMPT_PATH = "prompt/guidance-prompt.st";
    private static final double AMBIGUITY_RATIO = 0.85D;
    private static final double MIN_GUIDANCE_SCORE = 0.15D;
    private static final int MAX_GUIDANCE_OPTIONS = 3;

    private final RagIntentCatalogService catalogService;
    private final PromptTemplateLoader promptTemplateLoader;

    public RagGuidanceService(RagIntentCatalogService catalogService,
                              PromptTemplateLoader promptTemplateLoader) {
        this.catalogService = catalogService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    @RagTraceNode(name = "intent-guidance", type = "GUIDANCE")
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        AmbiguityGroup group = findAmbiguityGroup(subIntents);
        if (group == null || CollUtil.isEmpty(group.optionIds())) {
            return GuidanceDecision.none();
        }

        List<String> systemNames = resolveOptionNames(group.optionIds());
        if (shouldSkipGuidance(question, systemNames)) {
            return GuidanceDecision.none();
        }

        return GuidanceDecision.prompt(buildPrompt(group.topicName(), group.optionIds()));
    }

    private AmbiguityGroup findAmbiguityGroup(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return null;
        }

        List<NodeScore> candidates = filterCandidates(subIntents.get(0).nodeScores());
        if (candidates.size() < 2) {
            return null;
        }

        Map<String, List<NodeScore>> grouped = candidates.stream()
                .filter(score -> score.node() != null && StrUtil.isNotBlank(score.node().name))
                .collect(Collectors.groupingBy(score -> normalizeName(score.node().name)));

        Optional<AmbiguityCandidates> best = grouped.entrySet().stream()
                .map(entry -> new AmbiguityCandidates(resolveTopicName(entry.getValue()), sortByScore(entry.getValue())))
                .filter(entry -> entry.scores().size() > 1)
                .filter(entry -> passScoreRatio(entry.scores()))
                .filter(entry -> hasMultipleSystems(entry.scores()))
                .max(Comparator.comparingDouble(entry -> entry.scores().get(0).score()));

        if (best.isEmpty()) {
            return null;
        }

        List<String> optionIds = trimOptions(collectSystemOptions(best.get().scores()));
        if (optionIds.size() < 2) {
            return null;
        }
        return new AmbiguityGroup(best.get().topicName(), optionIds);
    }

    private List<NodeScore> filterCandidates(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return scores.stream()
                .filter(score -> score != null && score.node() != null && score.score() >= MIN_GUIDANCE_SCORE)
                .toList();
    }

    private List<NodeScore> sortByScore(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .toList();
    }

    private boolean passScoreRatio(List<NodeScore> scores) {
        if (scores.size() < 2) {
            return false;
        }
        double top = scores.get(0).score();
        double second = scores.get(1).score();
        if (top <= 0D) {
            return false;
        }
        return second / top >= AMBIGUITY_RATIO;
    }

    private boolean hasMultipleSystems(List<NodeScore> scores) {
        Set<String> systemIds = scores.stream()
                .map(NodeScore::node)
                .map(this::resolveSystemNodeId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        return systemIds.size() > 1;
    }

    private List<String> collectSystemOptions(List<NodeScore> scores) {
        Set<String> optionIds = new LinkedHashSet<>();
        for (NodeScore score : scores) {
            String systemId = resolveSystemNodeId(score.node());
            if (StrUtil.isNotBlank(systemId)) {
                optionIds.add(systemId);
            }
        }
        return new ArrayList<>(optionIds);
    }

    private String resolveSystemNodeId(RagIntentNode node) {
        if (node == null) {
            return "";
        }
        RagIntentNode current = node;
        while (current != null) {
            if (current.isSystem()) {
                return current.intentCode;
            }
            if (StrUtil.isBlank(current.parentCode)) {
                return current.intentCode;
            }
            current = catalogService.findByIntentCode(current.parentCode);
        }
        return "";
    }

    private List<String> resolveOptionNames(List<String> optionIds) {
        if (CollUtil.isEmpty(optionIds)) {
            return List.of();
        }
        return optionIds.stream()
                .map(catalogService::findByIntentCode)
                .filter(node -> node != null)
                .map(this::resolveDisplayName)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private boolean shouldSkipGuidance(String question, List<String> systemNames) {
        if (StrUtil.isBlank(question) || CollUtil.isEmpty(systemNames)) {
            return false;
        }
        String normalizedQuestion = normalizeName(question);
        for (String name : systemNames) {
            if (StrUtil.isBlank(name)) {
                continue;
            }
            String alias = normalizeName(name);
            if (alias.length() >= 2 && normalizedQuestion.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private String buildPrompt(String topicName, List<String> optionIds) {
        StringBuilder renderedOptions = new StringBuilder();
        for (int i = 0; i < optionIds.size(); i++) {
            RagIntentNode node = catalogService.findByIntentCode(optionIds.get(i));
            renderedOptions.append(i + 1).append(") ")
                    .append(node == null ? optionIds.get(i) : resolveDisplayName(node))
                    .append("\n");
        }
        return promptTemplateLoader.render(
                GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, ""),
                        "options", renderedOptions.toString().trim()
                )
        );
    }

    private String resolveTopicName(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores) || scores.get(0).node() == null) {
            return "";
        }
        RagIntentNode node = scores.get(0).node();
        return StrUtil.blankToDefault(node.name, node.intentCode);
    }

    private List<String> trimOptions(List<String> optionIds) {
        if (optionIds.size() <= MAX_GUIDANCE_OPTIONS) {
            return optionIds;
        }
        return optionIds.subList(0, MAX_GUIDANCE_OPTIONS);
    }

    private String resolveDisplayName(RagIntentNode node) {
        return StrUtil.blankToDefault(node.fullPath, StrUtil.blankToDefault(node.name, node.intentCode));
    }

    private String normalizeName(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "");
    }

    private record AmbiguityCandidates(String topicName, List<NodeScore> scores) {
    }

    private record AmbiguityGroup(String topicName, List<String> optionIds) {
    }
}
