package com.personalblog.ragbackend.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.rag.config.GuidanceProperties;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.IntentNodeRegistry;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.NodeScoreFilters;
import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
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
public class IntentGuidanceService {
    private final GuidanceProperties guidanceProperties;
    private final IntentNodeRegistry intentNodeRegistry;
    private final PromptTemplateLoader promptTemplateLoader;

    public IntentGuidanceService(GuidanceProperties guidanceProperties,
                                 IntentNodeRegistry intentNodeRegistry,
                                 PromptTemplateLoader promptTemplateLoader) {
        this.guidanceProperties = guidanceProperties;
        this.intentNodeRegistry = intentNodeRegistry;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        if (!Boolean.TRUE.equals(guidanceProperties.getEnabled())) {
            return GuidanceDecision.none();
        }

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
                .filter(score -> score != null && score.node() != null && StrUtil.isNotBlank(score.node().name))
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
        return NodeScoreFilters.kb(scores, RAGConstant.INTENT_MIN_SCORE);
    }

    private List<String> resolveOptionNames(List<String> optionIds) {
        if (CollUtil.isEmpty(optionIds)) {
            return List.of();
        }
        return optionIds.stream()
                .map(intentNodeRegistry::getNodeById)
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

    private List<String> collectSystemOptions(List<NodeScore> scores) {
        Set<String> optionIds = new LinkedHashSet<>();
        for (NodeScore score : scores) {
            String systemId = resolveSystemNodeId(score == null ? null : score.node());
            if (StrUtil.isNotBlank(systemId)) {
                optionIds.add(systemId);
            }
        }
        return new ArrayList<>(optionIds);
    }

    private String resolveSystemNodeId(IntentNode node) {
        if (node == null) {
            return "";
        }
        IntentNode current = node;
        IntentNode parent = fetchParent(current);
        for (; ; ) {
            if (current.isSystem()) {
                return current.getId();
            }
            if (parent == null) {
                return current.getId();
            }
            current = parent;
            parent = fetchParent(current);
        }
    }

    private IntentNode fetchParent(IntentNode node) {
        if (node == null || StrUtil.isBlank(node.getParentId())) {
            return null;
        }
        return intentNodeRegistry.getNodeById(node.getParentId());
    }

    private List<NodeScore> sortByScore(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::score).reversed())
                .toList();
    }

    private boolean passScoreRatio(List<NodeScore> group) {
        if (group.size() < 2) {
            return false;
        }
        double top = group.get(0).score();
        double second = group.get(1).score();
        if (top <= 0D) {
            return false;
        }
        double ratio = second / top;
        return ratio >= Optional.ofNullable(guidanceProperties.getAmbiguityScoreRatio()).orElse(0.0D);
    }

    private List<String> trimOptions(List<String> optionIds) {
        int maxOptions = Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(optionIds.size());
        if (optionIds.size() <= maxOptions) {
            return optionIds;
        }
        return optionIds.subList(0, maxOptions);
    }

    private String buildPrompt(String topicName, List<String> optionIds) {
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            IntentNode node = intentNodeRegistry.getNodeById(id);
            String name = node == null || StrUtil.isBlank(node.getName()) ? id : resolveDisplayName(node);
            options.append(i + 1).append(") ").append(name).append("\n");
        }
        return promptTemplateLoader.render(
                RAGConstant.GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, ""),
                        "options", options.toString().trim()
                )
        );
    }

    private String resolveDisplayName(IntentNode node) {
        return StrUtil.blankToDefault(node.getFullPath(), StrUtil.blankToDefault(node.getName(), node.getId()));
    }

    private String resolveTopicName(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores) || scores.get(0).node() == null) {
            return "";
        }
        IntentNode node = scores.get(0).node();
        return StrUtil.blankToDefault(node.getName(), node.getId());
    }

    private boolean hasMultipleSystems(List<NodeScore> group) {
        Set<String> systems = group.stream()
                .map(NodeScore::node)
                .map(this::resolveSystemNodeId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        return systems.size() > 1;
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "");
    }

    private record AmbiguityCandidates(String topicName, List<NodeScore> scores) {
    }

    private record AmbiguityGroup(String topicName, List<String> optionIds) {
    }
}
