package com.personalblog.ragbackend.ingestion.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.domain.settings.ChunkerSettings;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategy;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategyFactory;
import com.personalblog.ragbackend.knowledge.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ChunkerNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final ChunkingStrategyFactory chunkingStrategyFactory;

    public ChunkerNode(ObjectMapper objectMapper, ChunkingStrategyFactory chunkingStrategyFactory) {
        this.objectMapper = objectMapper;
        this.chunkingStrategyFactory = chunkingStrategyFactory;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.CHUNKER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        String text = StringUtils.hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
        if (!StringUtils.hasText(text)) {
            return NodeResult.fail(new IllegalArgumentException("chunk text is required"));
        }
        ChunkerSettings settings = parseSettings(config.getSettings());
        ChunkingMode mode = settings.getStrategy() == null ? ChunkingMode.STRUCTURE_AWARE : settings.getStrategy();
        ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(mode);
        TextChunkingOptions options = new TextChunkingOptions(
                settings.getChunkSize() == null || settings.getChunkSize() <= 0 ? 512 : settings.getChunkSize(),
                settings.getChunkSize() == null || settings.getChunkSize() <= 0 ? 768 : Math.max(settings.getChunkSize(), settings.getChunkSize() + Math.max(0, settings.getOverlapSize() == null ? 0 : settings.getOverlapSize())),
                settings.getOverlapSize() == null || settings.getOverlapSize() < 0 ? 128 : settings.getOverlapSize(),
                1000
        );
        List<DocumentChunk> chunks = strategy.chunk(text, options);
        List<VectorChunk> vectorChunks = chunks.stream()
                .map(chunk -> VectorChunk.builder()
                        .index(chunk.chunkIndex())
                        .content(chunk.content())
                        .metadata(new java.util.HashMap<>())
                        .build())
                .collect(Collectors.toList());
        context.setChunks(vectorChunks);
        return NodeResult.ok("chunked " + vectorChunks.size() + " chunks");
    }

    private ChunkerSettings parseSettings(com.fasterxml.jackson.databind.JsonNode node) {
        ChunkerSettings settings = node == null || node.isNull()
                ? ChunkerSettings.builder().build()
                : objectMapper.convertValue(node, ChunkerSettings.class);
        if (settings.getChunkSize() == null || settings.getChunkSize() <= 0) {
            settings.setChunkSize(512);
        }
        if (settings.getOverlapSize() == null || settings.getOverlapSize() < 0) {
            settings.setOverlapSize(128);
        }
        if (settings.getStrategy() == null) {
            settings.setStrategy(ChunkingMode.STRUCTURE_AWARE);
        }
        return settings;
    }
}
