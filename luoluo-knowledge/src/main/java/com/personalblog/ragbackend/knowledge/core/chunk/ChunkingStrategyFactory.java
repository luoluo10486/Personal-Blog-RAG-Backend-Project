package com.personalblog.ragbackend.knowledge.core.chunk;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ChunkingStrategyFactory {
    private final Map<ChunkingMode, ChunkingStrategy> strategyMap;

    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies) {
        Map<ChunkingMode, ChunkingStrategy> map = new EnumMap<>(ChunkingMode.class);
        for (ChunkingStrategy strategy : strategies) {
            ChunkingStrategy previous = map.putIfAbsent(strategy.getType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate chunking strategy: " + strategy.getType());
            }
        }
        this.strategyMap = Map.copyOf(map);
    }

    public Optional<ChunkingStrategy> findStrategy(ChunkingMode type) {
        return Optional.ofNullable(strategyMap.get(type));
    }

    public ChunkingStrategy requireStrategy(ChunkingMode type) {
        return findStrategy(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown chunking strategy: " + type));
    }
}
