package com.personalblog.ragbackend.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolSchema {

    private String name;
    private String description;
    private InputSchema inputSchema;

    public McpToolSchema() {
    }

    public McpToolSchema(String name, String description, InputSchema inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public InputSchema getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(InputSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputSchema {
        private String type = "object";
        private Map<String, PropertyDef> properties;
        private List<String> required;

        public InputSchema() {
        }

        public InputSchema(String type, Map<String, PropertyDef> properties, List<String> required) {
            this.type = type;
            this.properties = properties;
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, PropertyDef> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, PropertyDef> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertyDef {
        private String type;
        private String description;
        @JsonProperty("enum")
        private List<String> enumValues;

        public PropertyDef() {
        }

        public PropertyDef(String type, String description, List<String> enumValues) {
            this.type = type;
            this.description = description;
            this.enumValues = enumValues;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
        }
    }
}
