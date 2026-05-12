package com.personalblog.ragbackend.knowledge.dto.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class KnowledgeDocumentPageRequest extends Page {
    private String status;
    private String keyword;

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
}
