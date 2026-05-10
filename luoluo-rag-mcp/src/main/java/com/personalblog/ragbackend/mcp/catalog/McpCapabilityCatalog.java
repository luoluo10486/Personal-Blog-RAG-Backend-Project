package com.personalblog.ragbackend.mcp.catalog;

import java.util.List;
import java.util.Map;

public interface McpCapabilityCatalog {

    List<ToolMetadata> tools();

    List<ResourceMetadata> resources();

    List<PromptMetadata> prompts();

    default CapabilitySnapshot snapshot() {
        return new CapabilitySnapshot(tools(), resources(), prompts());
    }

    record CapabilitySnapshot(
            List<ToolMetadata> tools,
            List<ResourceMetadata> resources,
            List<PromptMetadata> prompts
    ) {
    }

    record ToolMetadata(
            String toolId,
            String description,
            Map<String, ParameterMetadata> parameters,
            boolean requireUserId
    ) {
    }

    record ParameterMetadata(
            String description,
            String type,
            boolean required,
            Object defaultValue,
            List<String> enumValues
    ) {
    }

    record ResourceMetadata(
            String uri,
            String name,
            String description,
            String mimeType
    ) {
    }

    record PromptMetadata(
            String name,
            String description,
            List<PromptArgumentMetadata> arguments
    ) {
    }

    record PromptArgumentMetadata(
            String name,
            String description,
            boolean required
    ) {
    }
}
