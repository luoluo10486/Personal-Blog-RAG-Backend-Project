package com.personalblog.ragbackend.knowledge.controller.vo;

import lombok.Data;

/**
 * Document search result view object.
 */
@Data
public class KnowledgeDocumentSearchVO {
    private String id;
    private String kbId;
    private String docName;
    private String kbName;
}
