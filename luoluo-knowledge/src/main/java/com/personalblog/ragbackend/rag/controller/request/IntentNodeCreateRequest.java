package com.personalblog.ragbackend.rag.controller.request;

import java.util.List;

public class IntentNodeCreateRequest {
    private String kbId;
    private String intentCode;
    private String name;
    private Integer level;
    private String parentCode;
    private String description;
    private List<String> examples;
    private String mcpToolId;
    private Integer topK;
    private Integer kind;
    private Integer sortOrder;
    private Integer enabled;
    private String promptSnippet;
    private String promptTemplate;
    private String paramPromptTemplate;

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getIntentCode() {
        return intentCode;
    }

    public void setIntentCode(String intentCode) {
        this.intentCode = intentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

    public String getMcpToolId() {
        return mcpToolId;
    }

    public void setMcpToolId(String mcpToolId) {
        this.mcpToolId = mcpToolId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getKind() {
        return kind;
    }

    public void setKind(Integer kind) {
        this.kind = kind;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getPromptSnippet() {
        return promptSnippet;
    }

    public void setPromptSnippet(String promptSnippet) {
        this.promptSnippet = promptSnippet;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getParamPromptTemplate() {
        return paramPromptTemplate;
    }

    public void setParamPromptTemplate(String paramPromptTemplate) {
        this.paramPromptTemplate = paramPromptTemplate;
    }
}
