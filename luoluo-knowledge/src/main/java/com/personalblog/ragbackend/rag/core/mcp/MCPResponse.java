package com.personalblog.ragbackend.rag.core.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {

    @Builder.Default
    private boolean success = true;
    private String toolId;
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    private String textResult;
    private String errorMessage;
    private String errorCode;
    private long costMs;

    public static MCPResponse success(String toolId, String textResult) {
        return MCPResponse.builder()
                .success(true)
                .toolId(toolId)
                .textResult(textResult)
                .build();
    }

    public static MCPResponse success(String toolId, String textResult, Map<String, Object> data) {
        return MCPResponse.builder()
                .success(true)
                .toolId(toolId)
                .textResult(textResult)
                .data(data)
                .build();
    }

    public static MCPResponse error(String toolId, String errorCode, String errorMessage) {
        return MCPResponse.builder()
                .success(false)
                .toolId(toolId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
