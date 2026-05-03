package com.personalblog.ragbackend.knowledge.core.chunk;

public record TextChunkingOptions(
        int targetChunkSize,
        int maxChunkSize,
        int overlapSize,
        int maxChunkCount
) {
    public TextChunkingOptions {
        targetChunkSize = Math.max(1, targetChunkSize);
        overlapSize = Math.max(0, overlapSize);
        maxChunkSize = Math.max(targetChunkSize, maxChunkSize);
        maxChunkCount = Math.max(1, maxChunkCount);
    }
}
