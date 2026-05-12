package com.personalblog.ragbackend.knowledge.dto.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class KnowledgeChunkPageRequest extends Page {
    private Integer enabled;

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
