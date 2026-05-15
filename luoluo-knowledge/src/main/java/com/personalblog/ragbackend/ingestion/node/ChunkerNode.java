package com.personalblog.ragbackend.ingestion.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.framework.exception.ClientException;
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
import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ChunkerNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final EmbeddingService embeddingService;

    public ChunkerNode(ObjectMapper objectMapper,
                       ChunkingStrategyFactory chunkingStrategyFactory,
                       EmbeddingService embeddingService) {
        this.objectMapper = objectMapper;
        this.chunkingStrategyFactory = chunkingStrategyFactory;
        this.embeddingService = embeddingService;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.CHUNKER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        String text = StringUtils.hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
        if (!StringUtils.hasText(text)) {
            return NodeResult.fail(new ClientException("chunk text is required"));
        }
        ChunkerSettings settings = parseSettings(config.getSettings());
        ChunkingMode mode = settings.getStrategy() == null ? ChunkingMode.STRUCTURE_AWARE : settings.getStrategy();
        ChunkingStrategy strategy = chunkingStrategyFactory.requireStrategy(mode);
        if (strategy == null) {
            return NodeResult.fail(new ClientException("chunk strategy is required"));
        }
        int chunkSize = settings.getChunkSize() == null || settings.getChunkSize() <= 0 ? 512 : settings.getChunkSize();
        int overlapSize = settings.getOverlapSize() == null || settings.getOverlapSize() < 0 ? 128 : settings.getOverlapSize();
        TextChunkingOptions options = new TextChunkingOptions(
                chunkSize,
                chunkSize + Math.max(0, overlapSize),
                overlapSize,
                1000
        );
        long chunkStart = System.currentTimeMillis();
        List<DocumentChunk> chunks = strategy.chunk(text, options);
        long chunkDurationMs = System.currentTimeMillis() - chunkStart;
        List<VectorChunk> vectorChunks = chunks.stream()
                .map(chunk -> VectorChunk.builder()
                        .index(chunk.chunkIndex())
                        .content(chunk.content())
                        .metadata(new java.util.HashMap<>())
                        .build())
                .collect(Collectors.toList());
        long embedStart = System.currentTimeMillis();
        List<List<Float>> embeddings = embeddingService.embedBatch(vectorChunks.stream().map(VectorChunk::getContent).toList());
        long embedDurationMs = System.currentTimeMillis() - embedStart;
        if (embeddings == null || embeddings.size() != vectorChunks.size()) {
            return NodeResult.fail(new ClientException("embedding result size mismatch"));
        }
        for (int i = 0; i < vectorChunks.size(); i++) {
            vectorChunks.get(i).setEmbedding(toArray(embeddings.get(i)));
        }
        context.setChunks(vectorChunks);
        Map<String, Object> metadata = new HashMap<>(context.getMetadata() == null ? Map.of() : context.getMetadata());
        context.setMetadata(metadata);
        metadata.put("chunkDurationMs", chunkDurationMs);
        metadata.put("embedDurationMs", embedDurationMs);
        metadata.put("chunkCount", vectorChunks.size());
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

    private float[] toArray(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return new float[0];
        }
        float[] values = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            values[i] = embedding.get(i) == null ? 0F : embedding.get(i);
        }
        return values;
    }
}
