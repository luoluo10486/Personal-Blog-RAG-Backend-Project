package com.personalblog.ragbackend.knowledge.dto.admin;

public class KnowledgeChunkPageRequest extends PageRequest {
    private Integer enabled;

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
