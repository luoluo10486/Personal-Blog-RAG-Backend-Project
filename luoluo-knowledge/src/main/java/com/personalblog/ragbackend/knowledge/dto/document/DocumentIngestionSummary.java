package com.personalblog.ragbackend.knowledge.dto.document;

public record DocumentIngestionSummary(
        boolean success,
        String baseCode,
        String collectionName,
        String embeddingModel,
        Long knowledgeBaseId,
        Long documentId,
        int chunkCount,
        int embeddedChunkCount,
        boolean vectorIndexed,
        String errorMessage
) {
    public static DocumentIngestionSummary success(String baseCode,
                                                   String collectionName,
                                                   String embeddingModel,
                                                   Long knowledgeBaseId,
                                                   Long documentId,
                                                   int chunkCount,
                                                   int embeddedChunkCount,
                                                   boolean vectorIndexed) {
        return new DocumentIngestionSummary(
                true,
                baseCode,
                collectionName,
                embeddingModel,
                knowledgeBaseId,
                documentId,
                chunkCount,
                embeddedChunkCount,
                vectorIndexed,
                null
        );
    }

    public static DocumentIngestionSummary failure(String baseCode, String errorMessage) {
        return new DocumentIngestionSummary(
                false,
                baseCode,
                null,
                null,
                null,
                null,
                0,
                0,
                false,
                errorMessage
        );
    }
}
