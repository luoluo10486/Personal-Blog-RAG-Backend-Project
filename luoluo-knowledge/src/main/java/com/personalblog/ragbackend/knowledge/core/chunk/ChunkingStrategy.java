package com.personalblog.ragbackend.knowledge.core.chunk;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;

import java.util.List;

public interface ChunkingStrategy {

    ChunkingMode getType();

    List<DocumentChunk> chunk(String text, TextChunkingOptions options);
}
