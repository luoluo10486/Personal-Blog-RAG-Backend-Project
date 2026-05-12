package com.personalblog.ragbackend.knowledge.dto.admin;

public record KnowledgeDocumentSearchView(
        String id,
        String kbId,
        String docName,
        String kbName
) {
}
