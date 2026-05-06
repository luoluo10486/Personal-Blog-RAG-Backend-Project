package com.personalblog.ragbackend.knowledge.dto.admin;

public class KnowledgeDocumentPageRequest extends PageRequest {
    private String status;
    private String keyword;
    private Integer enabled;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
