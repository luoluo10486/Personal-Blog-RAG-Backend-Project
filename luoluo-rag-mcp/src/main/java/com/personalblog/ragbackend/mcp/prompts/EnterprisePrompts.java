package com.personalblog.ragbackend.mcp.prompts;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EnterprisePrompts {

    public McpSchema.Prompt knowledgeQaPromptDefinition() {
        return new McpSchema.Prompt(
                "knowledge-qa",
                "知识库问答模板，基于检索到的知识片段回答用户问题，包含引用规则和兜底策略",
                List.of(
                        new McpSchema.PromptArgument("context", "检索到的知识片段，多个片段用换行分隔，每个片段带编号", true),
                        new McpSchema.PromptArgument("question", "用户的原始问题", true)
                )
        );
    }

    public McpSchema.GetPromptResult knowledgeQaPrompt(McpSchema.GetPromptRequest request) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        String context = requiredArg(arguments, "context");
        String question = requiredArg(arguments, "question");

        String userMessage = """
                你是一个企业知识库助手。请严格遵守以下规则：

                【回答规则】
                1. 只基于「参考资料」中的内容回答，不要使用你自己的知识。
                2. 如果参考资料中没有相关信息，请回答：抱歉，我在知识库中没有找到相关信息，建议您联系人工客服获取帮助。
                3. 不要编造、推测或补充参考资料中没有的细节。

                【引用规则】
                1. 回答中引用参考资料时，使用 [编号] 标注来源，例如 [1]、[2]。
                2. 只引用你实际使用的片段，不要空挂引用。
                3. 如果多个片段支持同一个观点，可以同时引用，例如 [1][2]。

                【格式要求】
                1. 先给结论，再给详细解释。
                2. 使用简洁的中文回答。
                3. 如果信息有冲突，以更新时间较近的片段为准。

                参考资料：
                %s

                问题：%s
                """.formatted(context, question);

        return new McpSchema.GetPromptResult(
                "知识库问答",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(userMessage)))
        );
    }

    public McpSchema.Prompt docSummaryPromptDefinition() {
        return new McpSchema.Prompt(
                "doc-summary",
                "文档摘要生成模板，将长文档压缩为指定长度的结构化摘要",
                List.of(
                        new McpSchema.PromptArgument("document", "需要摘要的文档内容", true),
                        new McpSchema.PromptArgument("maxLength", "摘要的最大字数，默认 200", false)
                )
        );
    }

    public McpSchema.GetPromptResult docSummaryPrompt(McpSchema.GetPromptRequest request) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        String document = requiredArg(arguments, "document");
        int length = optionalInt(arguments.get("maxLength"), 200);

        String userMessage = """
                你是一个专业的文档摘要助手。请按以下要求生成摘要：

                1. 摘要长度不超过 %d 字。
                2. 保留文档的核心观点和关键数据。
                3. 使用以下结构：
                   - 【主题】一句话概括文档主题
                   - 【要点】3~5 个核心要点，每个要点一句话
                   - 【结论】一句话总结
                4. 不要添加文档中没有的信息。
                5. 使用简洁的中文。

                请为以下文档生成摘要：

                %s
                """.formatted(length, document);

        return new McpSchema.GetPromptResult(
                "文档摘要",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(userMessage)))
        );
    }

    private String requiredArg(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + name);
        }
        return String.valueOf(value).trim();
    }

    private int optionalInt(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
