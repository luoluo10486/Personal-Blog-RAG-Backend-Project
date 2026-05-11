package com.personalblog.ragbackend.rag.core.intent;

public class RagIntentNode {
    public Long id;
    public String intentCode;
    public String name;
    public Integer level;
    public String parentCode;
    public String description;
    public String examples;
    public String collectionName;
    public Integer topK;
    public String mcpToolId;
    public Integer kind;
    public String promptSnippet;
    public String promptTemplate;
    public String paramPromptTemplate;
    public Integer sortOrder;
    public String fullPath;

    public boolean isSystem() {
        return kind != null && kind == 1;
    }

    public boolean isMcp() {
        return mcpToolId != null && !mcpToolId.isBlank();
    }

    public boolean isKb() {
        return !isSystem() && !isMcp();
    }
}
