package com.personalblog.ragbackend.mcp.config;

import com.personalblog.ragbackend.mcp.prompts.EnterprisePrompts;
import com.personalblog.ragbackend.mcp.resources.EnterpriseResources;
import com.personalblog.ragbackend.mcp.tools.RagMcpTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider ragToolProvider(RagMcpTools ragMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragMcpTools)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> syncResources(EnterpriseResources enterpriseResources) {
        return List.of(
                new McpServerFeatures.SyncResourceSpecification(
                        enterpriseResources.returnPolicyResource(),
                        (exchange, request) -> readResource(request, enterpriseResources)
                ),
                new McpServerFeatures.SyncResourceSpecification(
                        enterpriseResources.orderDetailResource(),
                        (exchange, request) -> readResource(request, enterpriseResources)
                )
        );
    }

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> syncPrompts(EnterprisePrompts enterprisePrompts) {
        return List.of(
                new McpServerFeatures.SyncPromptSpecification(
                        enterprisePrompts.knowledgeQaPromptDefinition(),
                        (exchange, request) -> enterprisePrompts.knowledgeQaPrompt(request)
                ),
                new McpServerFeatures.SyncPromptSpecification(
                        enterprisePrompts.docSummaryPromptDefinition(),
                        (exchange, request) -> enterprisePrompts.docSummaryPrompt(request)
                )
        );
    }

    private McpSchema.ReadResourceResult readResource(
            McpSchema.ReadResourceRequest request,
            EnterpriseResources enterpriseResources
    ) {
        String resourceUri = request.uri();
        if (enterpriseResources.isReturnPolicy(resourceUri)) {
            return enterpriseResources.getReturnPolicy();
        }
        if (enterpriseResources.isOrderDetail(resourceUri)) {
            return enterpriseResources.getOrderDetail(resourceUri);
        }
        throw new IllegalArgumentException("Unsupported resource URI: " + resourceUri);
    }
}
