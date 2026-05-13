package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.personalblog.ragbackend.rag.enums.IntentKind;
import com.personalblog.ragbackend.rag.enums.IntentLevel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RagIntentNode {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

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
    public String kbId;
    public String parentId;
    public List<RagIntentNode> children = new ArrayList<>();

    public String getId() {
        return intentCode;
    }

    public void setId(String id) {
        this.intentCode = id;
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

    public IntentLevel getLevel() {
        return IntentLevel.fromCode(level);
    }

    public void setLevel(IntentLevel level) {
        this.level = level == null ? null : level.getCode();
    }

    public String getParentId() {
        return StrUtil.blankToDefault(parentId, parentCode);
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
        this.parentCode = parentId;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
        this.parentId = parentCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getExamples() {
        if (StrUtil.isBlank(examples)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = GSON.fromJson(examples, STRING_LIST_TYPE);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception ignored) {
            return Collections.singletonList(examples);
        }
    }

    public void setExamples(List<String> examples) {
        if (CollUtil.isEmpty(examples)) {
            this.examples = null;
            return;
        }
        this.examples = GSON.toJson(examples);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public String getMcpToolId() {
        return mcpToolId;
    }

    public void setMcpToolId(String mcpToolId) {
        this.mcpToolId = mcpToolId;
    }

    public IntentKind getKind() {
        return IntentKind.fromCode(kind);
    }

    public void setKind(IntentKind kind) {
        this.kind = kind == null ? null : kind.getCode();
    }

    public Integer getKindCode() {
        return kind;
    }

    public void setKindCode(Integer kind) {
        this.kind = kind;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public List<RagIntentNode> getChildren() {
        return children == null ? List.of() : children;
    }

    public void setChildren(List<RagIntentNode> children) {
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    public boolean isSystem() {
        return kind != null && kind == IntentKind.SYSTEM.getCode();
    }

    public boolean isMcp() {
        return mcpToolId != null && !mcpToolId.isBlank();
    }

    public boolean isKb() {
        return !isSystem() && !isMcp();
    }
}
