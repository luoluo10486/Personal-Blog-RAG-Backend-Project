package com.personalblog.ragbackend.knowledge.service.document;

import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingStrategyFactory;
import com.personalblog.ragbackend.knowledge.core.chunk.TextChunkingOptions;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeDocumentChunkService {
    private final KnowledgeProperties knowledgeProperties;
    private final ChunkingStrategyFactory chunkingStrategyFactory;

    public KnowledgeDocumentChunkService(KnowledgeProperties knowledgeProperties,
                                         ChunkingStrategyFactory chunkingStrategyFactory) {
        this.knowledgeProperties = knowledgeProperties;
        this.chunkingStrategyFactory = chunkingStrategyFactory;
    }

    public DocumentChunkResponse chunkParsedResult(ParseResult parseResult) {
        if (parseResult == null) {
            return DocumentChunkResponse.failure("解析结果不能为空");
        }
        if (!parseResult.success()) {
            return DocumentChunkResponse.failure(parseResult.errorMessage());
        }
        return chunkContent(parseResult.content(), parseResult.mimeType(), parseResult.metadata(), parseResult.contentLength());
    }

    public DocumentChunkResponse chunkText(String content) {
        if (content == null || content.isBlank()) {
            return DocumentChunkResponse.failure("文本内容不能为空");
        }
        return chunkContent(content, "text/plain", Map.of(), content.length());
    }

    private DocumentChunkResponse chunkContent(String content,
                                               String mimeType,
                                               Map<String, String> metadata,
                                               int contentLength) {
        TextChunkingOptions options = buildChunkingOptions();
        ChunkingMode mode = ChunkingMode.from(knowledgeProperties.getChunking().getStrategy());
        List<DocumentChunk> chunks = chunkingStrategyFactory.requireStrategy(mode).chunk(content, options);
        return DocumentChunkResponse.success(
                mimeType,
                metadata,
                contentLength,
                options.targetChunkSize(),
                options.maxChunkSize(),
                options.overlapSize(),
                chunks
        );
    }

    private TextChunkingOptions buildChunkingOptions() {
        int targetChunkSize = Math.max(1, knowledgeProperties.getChunking().getChunkSize());
        int overlapSize = Math.max(0, knowledgeProperties.getChunking().getChunkOverlap());
        int maxChunkSize = Math.max(targetChunkSize, targetChunkSize + Math.max(overlapSize, 300));
        int maxChunkCount = Math.max(1, knowledgeProperties.getChunking().getMaxChunkCount());
        return new TextChunkingOptions(targetChunkSize, maxChunkSize, overlapSize, maxChunkCount);
    }
}
