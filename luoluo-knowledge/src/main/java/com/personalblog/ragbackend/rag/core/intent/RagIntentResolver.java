package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.rag.core.rewrite.RewriteResult;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class RagIntentResolver {
    private static final int MAX_INTENT_COUNT = 8;
    private static final double MIN_SCORE = 0.15D;

    private final RagIntentCatalogService catalogService;
    private final IntentClassifier intentClassifier;
    private final Executor intentClassifyExecutor;

    public RagIntentResolver(RagIntentCatalogService catalogService,
                             @Qualifier("defaultIntentClassifier") IntentClassifier intentClassifier,
                             @Qualifier("intentClassifyThreadPoolExecutor") Executor intentClassifyExecutor) {
        this.catalogService = catalogService;
        this.intentClassifier = intentClassifier;
        this.intentClassifyExecutor = intentClassifyExecutor;
    }

    @RagTraceNode(name = "intent-resolve", type = "INTENT")
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        if (rewriteResult == null) {
            return List.of();
        }
        List<String> subQuestions = CollUtil.isNotEmpty(rewriteResult.subQuestions())
                ? rewriteResult.subQuestions()
                : List.of(StrUtil.blankToDefault(rewriteResult.rewrittenQuestion(), ""));
        if (CollUtil.isEmpty(subQuestions)) {
            return List.of(new SubQuestionIntent(
                    StrUtil.blankToDefault(rewriteResult.rewrittenQuestion(), ""),
                    classify(StrUtil.blankToDefault(rewriteResult.rewrittenQuestion(), ""))
            ));
        }
        List<CompletableFuture<SubQuestionIntent>> tasks = subQuestions.stream()
                .map(subQuestion -> CompletableFuture.supplyAsync(
                        () -> new SubQuestionIntent(subQuestion, classify(subQuestion)),
                        intentClassifyExecutor
                ))
                .toList();
        return capTotalIntents(tasks.stream().map(CompletableFuture::join).toList());
    }

    public List<SubQuestionIntent> resolve(String question) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        return resolve(new RewriteResult(safeQuestion, List.of(safeQuestion)));
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        return catalogService.mergeIntentGroup(subIntents);
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return catalogService.isSystemOnly(nodeScores);
    }

    private List<NodeScore> classify(String question) {
        return intentClassifier.classifyTargets(question).stream()
                .filter(score -> score != null && score.score() >= MIN_SCORE)
                .limit(MAX_INTENT_COUNT)
                .toList();
    }

    private List<SubQuestionIntent> capTotalIntents(List<SubQuestionIntent> subIntents) {
        int totalIntents = subIntents.stream()
                .mapToInt(subIntent -> subIntent.nodeScores() == null ? 0 : subIntent.nodeScores().size())
                .sum();
        if (totalIntents <= MAX_INTENT_COUNT) {
            return subIntents;
        }

        List<IntentCandidate> allCandidates = collectAllCandidates(subIntents);
        List<IntentCandidate> guaranteed = selectTopIntentPerSubQuestion(allCandidates, subIntents.size());
        int remaining = Math.max(0, MAX_INTENT_COUNT - guaranteed.size());
        List<IntentCandidate> additional = selectAdditionalIntents(allCandidates, guaranteed, remaining);
        return rebuildSubIntents(subIntents, guaranteed, additional);
    }

    private List<IntentCandidate> collectAllCandidates(List<SubQuestionIntent> subIntents) {
        List<IntentCandidate> candidates = new ArrayList<>();
        for (int subQuestionIndex = 0; subQuestionIndex < subIntents.size(); subQuestionIndex++) {
            List<NodeScore> nodeScores = subIntents.get(subQuestionIndex).nodeScores();
            if (CollUtil.isEmpty(nodeScores)) {
                continue;
            }
            for (NodeScore nodeScore : nodeScores) {
                candidates.add(new IntentCandidate(subQuestionIndex, nodeScore));
            }
        }
        candidates.sort((left, right) -> Double.compare(right.nodeScore().score(), left.nodeScore().score()));
        return candidates;
    }

    private List<IntentCandidate> selectTopIntentPerSubQuestion(List<IntentCandidate> allCandidates, int subQuestionCount) {
        List<IntentCandidate> topIntents = new ArrayList<>();
        boolean[] selected = new boolean[subQuestionCount];

        for (IntentCandidate candidate : allCandidates) {
            int index = candidate.subQuestionIndex();
            if (!selected[index]) {
                topIntents.add(candidate);
                selected[index] = true;
            }
            if (topIntents.size() == subQuestionCount) {
                break;
            }
        }
        return topIntents;
    }

    private List<IntentCandidate> selectAdditionalIntents(List<IntentCandidate> allCandidates,
                                                          List<IntentCandidate> guaranteedIntents,
                                                          int remaining) {
        if (remaining <= 0) {
            return List.of();
        }

        List<IntentCandidate> additional = new ArrayList<>();
        for (IntentCandidate candidate : allCandidates) {
            if (guaranteedIntents.contains(candidate)) {
                continue;
            }
            additional.add(candidate);
            if (additional.size() >= remaining) {
                break;
            }
        }
        return additional;
    }

    private List<SubQuestionIntent> rebuildSubIntents(List<SubQuestionIntent> originalSubIntents,
                                                      List<IntentCandidate> guaranteedIntents,
                                                      List<IntentCandidate> additionalIntents) {
        List<IntentCandidate> selectedIntents = new ArrayList<>(guaranteedIntents);
        selectedIntents.addAll(additionalIntents);

        Map<Integer, List<NodeScore>> groupedByIndex = new HashMap<>();
        for (IntentCandidate candidate : selectedIntents) {
            groupedByIndex.computeIfAbsent(candidate.subQuestionIndex(), ignored -> new ArrayList<>())
                    .add(candidate.nodeScore());
        }

        List<SubQuestionIntent> result = new ArrayList<>();
        for (int index = 0; index < originalSubIntents.size(); index++) {
            SubQuestionIntent original = originalSubIntents.get(index);
            result.add(new SubQuestionIntent(original.subQuestion(), groupedByIndex.getOrDefault(index, List.of())));
        }
        return result;
    }

    private record IntentCandidate(int subQuestionIndex, NodeScore nodeScore) {
    }
}
