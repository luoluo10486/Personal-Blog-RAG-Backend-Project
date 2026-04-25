package com.personalblog.ragbackend.infra.ai.rerank;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.model.ModelRoutingExecutor;
import com.personalblog.ragbackend.infra.ai.model.ModelSelector;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class RoutingRerankService implements RerankService {

    private final ModelSelector selector;
    private final ModelRoutingExecutor executor;
    private final Map<String, RerankClient> clientsByProvider;

    public RoutingRerankService(ModelSelector selector,
                                ModelRoutingExecutor executor,
                                List<RerankClient> clients) {
        this.selector = selector;
        this.executor = executor;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(RerankClient::provider, Function.identity()));
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        return executor.executeWithFallback(
                ModelCapability.RERANK,
                selector.selectRerankCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.rerank(query, candidates, topN, target)
        );
    }
}
