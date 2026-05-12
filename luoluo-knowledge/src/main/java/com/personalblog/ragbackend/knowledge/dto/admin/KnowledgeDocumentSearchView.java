package com.personalblog.ragbackend.knowledge.dto.admin;

public class KnowledgeDocumentSearchView {
    private String id;
    private String kbId;
    private String docName;
    private String kbName;

    public KnowledgeDocumentSearchView() {
    }

    public KnowledgeDocumentSearchView(String id, String kbId, String docName, String kbName) {
        this.id = id;
        this.kbId = kbId;
        this.docName = docName;
        this.kbName = kbName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }
}
