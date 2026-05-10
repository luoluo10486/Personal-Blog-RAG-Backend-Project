package com.personalblog.ragbackend.mcp.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog.ParameterMetadata;
import static com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog.PromptArgumentMetadata;
import static com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog.PromptMetadata;
import static com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog.ResourceMetadata;
import static com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog.ToolMetadata;

@Component
public class DefaultMcpCapabilityCatalog implements McpCapabilityCatalog {

    @Override
    public List<ToolMetadata> tools() {
        return List.of(
                tool(
                        "getRagStatus",
                        "查看当前正式知识库 RAG 服务状态、默认知识库和向量空间配置。",
                        map()
                ),
                tool(
                        "searchKnowledgeBase",
                        "在正式知识库链路中执行检索，返回命中的知识片段和向量空间信息。",
                        map(
                                "query", parameter("检索问题，例如：会员积分可以提现吗", "string", true, null, List.of()),
                                "topK", parameter("返回结果数量，范围 1 到 20，留空时默认 5", "integer", false, null, List.of()),
                                "baseCode", parameter("可选知识库编码，留空时使用默认知识库", "string", false, null, List.of())
                        )
                ),
                tool(
                        "generateKnowledgeAnswer",
                        "基于正式知识库链路生成最终回答，返回答案、引用和执行轨迹。",
                        map(
                                "query", parameter("用户问题，例如：订单发货后多久能看到物流信息", "string", true, null, List.of()),
                                "topK", parameter("检索并参与生成的候选片段数，范围 1 到 20，留空时默认 5", "integer", false, null, List.of()),
                                "baseCode", parameter("可选知识库编码，留空时使用默认知识库", "string", false, null, List.of())
                        )
                ),
                tool(
                        "chunkPlainText",
                        "按正式知识库切块规则对纯文本进行切块，适合在入库前预览 chunk 效果。",
                        map(
                                "content", parameter("需要切块的原始文本内容", "string", true, null, List.of())
                        )
                ),
                tool(
                        "previewKnowledgeCitations",
                        "查看正式知识库链路中当前问题的候选片段预览，便于人工核对召回质量。",
                        map(
                                "query", parameter("检索问题", "string", true, null, List.of()),
                                "topK", parameter("候选数量，范围 1 到 20，留空时默认 5", "integer", false, null, List.of()),
                                "baseCode", parameter("可选知识库编码，留空时使用默认知识库", "string", false, null, List.of())
                        )
                ),
                tool(
                        "describeMcpCapabilities",
                        "列出当前 MCP 服务的 tools、resources 和 prompts 元数据，便于和其他实现对齐。",
                        map()
                )
        );
    }

    @Override
    public List<ResourceMetadata> resources() {
        return List.of(
                new ResourceMetadata(
                        "docs://return-policy",
                        "退货政策",
                        "公司的退货政策文档，包含退货条件、时限、流程等信息",
                        "text/plain"
                ),
                new ResourceMetadata(
                        "order://{orderId}",
                        "订单详情",
                        "根据订单号查询订单详情。读取时请使用形如 order://ORD-12345 或 order://12345 的 URI。",
                        "application/json"
                )
        );
    }

    @Override
    public List<PromptMetadata> prompts() {
        return List.of(
                new PromptMetadata(
                        "knowledge-qa",
                        "知识库问答模板，基于检索到的知识片段回答用户问题，包含引用规则和兜底策略",
                        List.of(
                                new PromptArgumentMetadata("context", "检索到的知识片段，多个片段用换行分隔，每个片段带编号", true),
                                new PromptArgumentMetadata("question", "用户的原始问题", true)
                        )
                ),
                new PromptMetadata(
                        "doc-summary",
                        "文档摘要生成模板，将长文档压缩为指定长度的结构化摘要",
                        List.of(
                                new PromptArgumentMetadata("document", "需要摘要的文档内容", true),
                                new PromptArgumentMetadata("maxLength", "摘要的最大字数，默认 200", false)
                        )
                )
        );
    }

    private ToolMetadata tool(String toolId, String description, Map<String, ParameterMetadata> parameters) {
        return new ToolMetadata(toolId, description, parameters, false);
    }

    private ParameterMetadata parameter(String description, String type, boolean required, Object defaultValue, List<String> enumValues) {
        return new ParameterMetadata(description, type, required, defaultValue, enumValues);
    }

    private Map<String, ParameterMetadata> map(Object... entries) {
        Map<String, ParameterMetadata> parameters = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            parameters.put((String) entries[index], (ParameterMetadata) entries[index + 1]);
        }
        return parameters;
    }
}
