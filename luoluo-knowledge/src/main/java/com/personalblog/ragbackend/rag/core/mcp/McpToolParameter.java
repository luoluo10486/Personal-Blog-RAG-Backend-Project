package com.personalblog.ragbackend.rag.core.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolParameter {

    private String type;
    private String description;
    private boolean required;
    private Object defaultValue;
    private List<String> enumValues;
}
