package com.personalblog.ragbackend.rag.core.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPTool {

    private String toolId;
    private String description;
    private Map<String, ParameterDef> parameters;

    @Builder.Default
    private boolean requireUserId = true;

    private String mcpServerUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterDef {
        private String description;
        @Builder.Default
        private String type = "string";
        @Builder.Default
        private boolean required = false;
        private Object defaultValue;
        private List<String> enumValues;
    }
}
