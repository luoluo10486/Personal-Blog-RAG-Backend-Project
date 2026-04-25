package com.personalblog.ragbackend.infra.ai.model;

import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ModelSelector {
    private final AIModelProperties aiProperties;
    private final ModelHealthStore modelHealthStore;

    public ModelSelector(AIModelProperties aiProperties, ModelHealthStore modelHealthStore) {
        this.aiProperties = aiProperties;
        this.modelHealthStore = modelHealthStore;
    }

    public List<ModelTarget> selectChatCandidates(boolean thinking) {
        AIModelProperties.ModelGroup group = aiProperties.getChat();
        String firstChoice = thinking && hasText(group.getDeepThinkingModel())
                ? group.getDeepThinkingModel()
                : group.getDefaultModel();
        return selectCandidates(group, firstChoice, thinking);
    }

    public List<ModelTarget> selectEmbeddingCandidates() {
        return selectCandidates(aiProperties.getEmbedding(), aiProperties.getEmbedding().getDefaultModel(), false);
    }

    public List<ModelTarget> selectRerankCandidates() {
        return selectCandidates(aiProperties.getRerank(), aiProperties.getRerank().getDefaultModel(), false);
    }

    private List<ModelTarget> selectCandidates(AIModelProperties.ModelGroup group, String firstChoiceModelId, boolean thinking) {
        if (group == null || group.getCandidates() == null) {
            return List.of();
        }

        Map<String, AIModelProperties.ProviderConfig> providers = aiProperties.getProviders();
        return group.getCandidates().stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getEnabled()))
                .filter(candidate -> !thinking || Boolean.TRUE.equals(candidate.getSupportsThinking()))
                .sorted(Comparator
                        .comparing((AIModelProperties.ModelCandidate candidate) ->
                                !Objects.equals(resolveId(candidate), firstChoiceModelId))
                        .thenComparing(AIModelProperties.ModelCandidate::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(candidate -> resolveId(candidate), Comparator.nullsLast(String::compareTo)))
                .map(candidate -> buildTarget(candidate, providers))
                .filter(Objects::nonNull)
                .toList();
    }

    private ModelTarget buildTarget(AIModelProperties.ModelCandidate candidate,
                                    Map<String, AIModelProperties.ProviderConfig> providers) {
        String modelId = resolveId(candidate);
        if (modelHealthStore.isUnavailable(modelId)) {
            return null;
        }

        AIModelProperties.ProviderConfig provider = providers.get(candidate.getProvider());
        if (provider == null && !ModelProvider.NOOP.matches(candidate.getProvider())) {
            return null;
        }

        return new ModelTarget(modelId, candidate, provider);
    }

    public String resolveId(AIModelProperties.ModelCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        if (hasText(candidate.getId())) {
            return candidate.getId().trim();
        }
        return candidate.getProvider() + "::" + candidate.getModel();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
