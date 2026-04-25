package com.personalblog.ragbackend.infra.ai.embedding;

import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.exception.RemoteException;
import com.personalblog.ragbackend.infra.ai.model.ModelRoutingExecutor;
import com.personalblog.ragbackend.infra.ai.model.ModelSelector;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class RoutingEmbeddingService implements EmbeddingService {

    private final ModelSelector selector;
    private final ModelRoutingExecutor executor;
    private final Map<String, EmbeddingClient> clientsByProvider;

    public RoutingEmbeddingService(ModelSelector selector,
                                   ModelRoutingExecutor executor,
                                   List<EmbeddingClient> clients) {
        this.selector = selector;
        this.executor = executor;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(EmbeddingClient::provider, Function.identity()));
    }

    @Override
    public List<Float> embed(String text) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                selector.selectEmbeddingCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embed(text, target)
        );
    }

    @Override
    public List<Float> embed(String text, String modelId) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                List.of(resolveTarget(modelId)),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embed(text, target)
        );
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                selector.selectEmbeddingCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embedBatch(texts, target)
        );
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                List.of(resolveTarget(modelId)),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embedBatch(texts, target)
        );
    }

    @Override
    public int dimension() {
        return selector.selectEmbeddingCandidates().stream()
                .map(ModelTarget::candidate)
                .map(candidate -> candidate.getDimension() == null ? 0 : candidate.getDimension())
                .filter(value -> value > 0)
                .findFirst()
                .orElse(0);
    }

    private ModelTarget resolveTarget(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new RemoteException("Embedding 模型ID不能为空");
        }
        return selector.selectEmbeddingCandidates().stream()
                .filter(target -> modelId.equals(target.id()))
                .findFirst()
                .orElseThrow(() -> new RemoteException("Embedding 模型不可用: " + modelId));
    }
}
