package com.personalblog.ragbackend.dto.rag;

/**
 * RAG 回答中的单条引用信息。
 */
public record RagGenerationCitation(
        int index,
        String source,
        String docId,
        String title,
        String category,
        String sourceUrl,
        String chunkContent
) {
}
