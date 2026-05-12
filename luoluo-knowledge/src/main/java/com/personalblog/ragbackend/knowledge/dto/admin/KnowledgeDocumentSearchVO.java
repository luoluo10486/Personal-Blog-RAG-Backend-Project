package com.personalblog.ragbackend.knowledge.dto.admin;

import lombok.Data;

@Data
public class KnowledgeDocumentSearchVO {
    private String id;
    private String kbId;
    private String docName;
    private String kbName;
}
