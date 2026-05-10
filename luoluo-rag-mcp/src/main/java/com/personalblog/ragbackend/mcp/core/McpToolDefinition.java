package com.personalblog.ragbackend.mcp.core;

import java.util.List;
import java.util.Map;

public class McpToolDefinition {

    private String toolId;
    private String description;
    private Map<String, ParameterDef> parameters;
    private boolean requireUserId = true;

    public McpToolDefinition() {
    }

    public McpToolDefinition(String toolId, String description, Map<String, ParameterDef> parameters, boolean requireUserId) {
        this.toolId = toolId;
        this.description = description;
        this.parameters = parameters;
        this.requireUserId = requireUserId;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, ParameterDef> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ParameterDef> parameters) {
        this.parameters = parameters;
    }

    public boolean isRequireUserId() {
        return requireUserId;
    }

    public void setRequireUserId(boolean requireUserId) {
        this.requireUserId = requireUserId;
    }

    public static class ParameterDef {

        private String description;
        private String type = "string";
        private boolean required = false;
        private Object defaultValue;
        private List<String> enumValues;

        public ParameterDef() {
        }

        public ParameterDef(String description, String type, boolean required, Object defaultValue, List<String> enumValues) {
            this.description = description;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.enumValues = enumValues;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
        }
    }
}
