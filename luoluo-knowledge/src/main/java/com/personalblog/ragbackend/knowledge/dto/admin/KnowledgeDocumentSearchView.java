package com.personalblog.ragbackend.knowledge.dto.admin;

public record KnowledgeDocumentSearchView(
        Long id,
        Long kbId,
        String docName,
        String status,
        Integer enabled,
        Integer chunkCount
) {
}
