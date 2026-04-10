package com.personalblog.ragbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.dto.rag.RagGenerationCitation;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 生成演示服务：把检索结果拼成上下文，再调用模型生成带引用的答案。
 */
@Service
public class RagGenerationDemoService {
    private static final Logger log = LoggerFactory.getLogger(RagGenerationDemoService.class);

    private static final String FALLBACK_ANSWER = "根据现有资料，暂时无法回答该问题。建议您联系人工客服获取更多帮助。";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
    private static final int CHUNK_PREVIEW_LENGTH = 60;
    private static final int LOG_PREVIEW_LENGTH = 400;
    private static final String GENERATION_SYSTEM_PROMPT = """
            你是一名专业的电商客服助手。你的任务是根据【参考资料】中的信息，准确回答用户的问题。

            【角色与边界】
            - 你只负责回答与商品售后、退换货、物流配送、会员权益相关的问题。
            - 如果用户的问题超出这个范围，请礼貌说明，并引导回电商售后相关话题。
            - 不要回答品牌对比、价格预测、个人观点等主观性问题。

            【回答规则】
            1. 只基于【参考资料】中的内容回答问题，不要使用你自己的外部知识。
            2. 如果【参考资料】中没有足够信息，请明确回答："根据现有资料，暂时无法回答该问题。建议您联系人工客服获取更多帮助。"
            3. 不要编造资料中没有提到的数字、日期、金额或流程。
            4. 如果多条参考资料存在冲突，请指出冲突，并提醒以最新规则为准。

            【引用规则】
            - 回答时请引用参考资料编号，格式为 [1]、[2]。
            - 如果一句话同时使用多条资料，请在句末标注多个编号，例如 [1][3]。
            - 只引用实际使用过的参考资料。

            【格式要求】
            - 用简洁、友好的中文回答。
            - 先直接回答用户问题，再补充必要注意事项。
            """;
    private static final String FUNCTION_CALL_APPEND_PROMPT = """

            【工具使用说明】
            - 你会先收到一份“已召回资料目录”，其中只包含编号、标题、分类和摘要。
            - 如果要确认具体事实、时间、规则或细节，请优先调用工具，不要直接猜测。
            - 可用工具有：
              1. `listRetrievedChunks`：查看本次已召回资料的目录信息
              2. `getRetrievedChunkByIndex`：按编号查看某条资料的完整内容
            - 最终回答时，请继续使用资料编号作为引用编号，例如 [1]、[2]。
            """;

    private static final String FUNCTION_CALL_ENFORCEMENT_PROMPT = """

            【强制核验规则】
            - 如果用户问题涉及订单号、物流状态、具体时间、天数、金额、条件、时效、规则条款等可核验细节，必须先调用至少一个工具，再输出最终答案。
            - 即使资料目录中的摘要已经非常接近答案，也不能直接依据摘要作答，仍需调用 `getRetrievedChunkByIndex` 查看原文后再回答。
            - 如果回答依赖多条资料，请分别调用 `getRetrievedChunkByIndex` 核验相关编号，再综合回答。
            - 只有当问题非常泛化、且不涉及任何具体事实核验时，才可以不调用工具。
            """;

    private final ObjectMapper objectMapper;
    private final SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;
    private final SiliconFlowChatDemoService siliconFlowChatDemoService;

    public RagGenerationDemoService(
            ObjectMapper objectMapper,
            SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService,
            SiliconFlowChatDemoService siliconFlowChatDemoService
    ) {
        this.objectMapper = objectMapper;
        this.siliconFlowEmbeddingDemoService = siliconFlowEmbeddingDemoService;
        this.siliconFlowChatDemoService = siliconFlowChatDemoService;
    }

    public RagGenerationResponse generate(RagGenerationRequest request) {
        String query = request.query().trim();
        RagEmbeddingSearchResponse retrieval = siliconFlowEmbeddingDemoService.search(
                new RagEmbeddingSearchRequest(query, request.topK())
        );

        List<RetrievedChunk> chunks = toRetrievedChunks(retrieval.results());
        if (chunks.isEmpty()) {
            return new RagGenerationResponse(
                    query,
                    FALLBACK_ANSWER,
                    null,
                    "",
                    null,
                    0,
                    0,
                    0,
                    retrieval.embeddingModel(),
                    0,
                    retrieval.recallMode(),
                    retrieval.rerankApplied(),
                    retrieval.rerankProvider(),
                    retrieval.rerankModel(),
                    false,
                    List.of(),
                    List.of()
            );
        }

        String systemPrompt = resolveSystemPrompt(request.systemPrompt());
        String functionCallPrompt = buildFunctionCallPrompt(systemPrompt);
        String chunkCatalog = buildChunkCatalog(chunks);
        String toolUserMessage = buildToolUserMessage(chunkCatalog, query);
        ArrayNode tools = buildFunctionTools();

        log.info(
                "RAG 生成开始: query={}, retrievedChunkCount={}, recallMode={}, rerankApplied={}",
                query,
                chunks.size(),
                retrieval.recallMode(),
                retrieval.rerankApplied()
        );
        log.info("RAG function call 第一轮目录预览: query={}, chunkCatalog={}", query, abbreviateForLog(chunkCatalog));

        SiliconFlowChatDemoService.ToolChatRoundResponse firstRound = siliconFlowChatDemoService.chatWithTools(
                functionCallPrompt,
                toolUserMessage,
                tools
        );

        boolean functionCallApplied = !firstRound.toolCalls().isEmpty();
        List<String> calledTools = functionCallApplied
                ? firstRound.toolCalls().stream().map(SiliconFlowChatDemoService.ToolCall::name).distinct().toList()
                : List.of();

        log.info(
                "RAG function call 第一轮结果: query={}, functionCallApplied={}, calledTools={}, assistantContent={}, toolCalls={}",
                query,
                functionCallApplied,
                calledTools,
                abbreviateForLog(firstRound.content()),
                summarizeToolCalls(firstRound.toolCalls())
        );

        RagDemoChatResponse chatResponse;
        if (functionCallApplied) {
            List<SiliconFlowChatDemoService.ToolResult> toolResults = executeToolCalls(firstRound.toolCalls(), chunks);
            log.info(
                    "RAG function call 本地工具执行完成: query={}, toolResults={}",
                    query,
                    summarizeToolResults(toolResults)
            );
            chatResponse = siliconFlowChatDemoService.completeToolChat(
                    functionCallPrompt,
                    toolUserMessage,
                    firstRound.toolCalls(),
                    toolResults
            );
        } else {
            log.info("RAG function call 未触发，回退普通 RAG 生成: query={}", query);
            chatResponse = siliconFlowChatDemoService.chat(
                    new RagDemoChatRequest(systemPrompt, buildUserMessage(buildContext(chunks), query))
            );
        }

        List<RagGenerationCitation> citations = parseCitations(chatResponse.answer(), chunks);

        return new RagGenerationResponse(
                query,
                normalizeAnswer(chatResponse.answer()),
                chatResponse.requestId(),
                chatResponse.model(),
                chatResponse.finishReason(),
                chatResponse.promptTokens(),
                chatResponse.completionTokens(),
                chatResponse.totalTokens(),
                retrieval.embeddingModel(),
                chunks.size(),
                retrieval.recallMode(),
                retrieval.rerankApplied(),
                retrieval.rerankProvider(),
                retrieval.rerankModel(),
                functionCallApplied,
                calledTools,
                citations
        );
    }

    String buildContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder("【参考资料】\n\n");
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            context.append("[")
                    .append(index + 1)
                    .append("] 标题：")
                    .append(defaultText(chunk.title(), "未命名资料"))
                    .append(" | 文档ID：")
                    .append(defaultText(chunk.docId(), "未知"))
                    .append(" | 分类：")
                    .append(defaultText(chunk.category(), "general"))
                    .append("\n");
            context.append(chunk.content()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 第一轮只给模型看“资料目录”，让它先决定要不要调用工具拿更详细的内容。
     */
    String buildChunkCatalog(List<RetrievedChunk> chunks) {
        StringBuilder catalog = new StringBuilder("【已召回资料目录】\n\n");
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            catalog.append("[")
                    .append(index + 1)
                    .append("] 标题：")
                    .append(defaultText(chunk.title(), "未命名资料"))
                    .append(" | 文档ID：")
                    .append(defaultText(chunk.docId(), "未知"))
                    .append(" | 分类：")
                    .append(defaultText(chunk.category(), "general"))
                    .append(" | 摘要：")
                    .append(buildPreview(chunk.content()))
                    .append("\n");
        }
        return catalog.toString();
    }

    List<RagGenerationCitation> parseCitations(String answer, List<RetrievedChunk> chunks) {
        Set<Integer> citedIndexes = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            citedIndexes.add(Integer.parseInt(matcher.group(1)));
        }

        List<RagGenerationCitation> citations = new ArrayList<>();
        for (Integer citedIndex : citedIndexes) {
            if (citedIndex < 1 || citedIndex > chunks.size()) {
                continue;
            }
            RetrievedChunk chunk = chunks.get(citedIndex - 1);
            citations.add(new RagGenerationCitation(
                    citedIndex,
                    buildSource(chunk),
                    chunk.docId(),
                    chunk.title(),
                    chunk.category(),
                    chunk.sourceUrl(),
                    chunk.content()
            ));
        }
        return citations;
    }

    private List<RetrievedChunk> toRetrievedChunks(List<RagEmbeddingSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>(results.size());
        for (RagEmbeddingSearchResult result : results) {
            Map<String, String> metadata = result.metadata() == null ? Map.of() : result.metadata();
            String docId = metadata.getOrDefault("doc_id", "");
            String title = metadata.getOrDefault("title", "");
            String category = metadata.getOrDefault("category", "");
            String sourceUrl = metadata.getOrDefault("source_url", "");
            chunks.add(new RetrievedChunk(result.rank(), docId, title, category, sourceUrl, result.content()));
        }
        return chunks;
    }

    private String buildUserMessage(String context, String userQuery) {
        return context + "【用户问题】\n" + userQuery;
    }

    private String buildToolUserMessage(String chunkCatalog, String userQuery) {
        return chunkCatalog + "\n【用户问题】\n" + userQuery;
    }

    private String resolveSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return GENERATION_SYSTEM_PROMPT;
        }
        return systemPrompt.trim();
    }

    private String buildFunctionCallPrompt(String systemPrompt) {
        return systemPrompt + FUNCTION_CALL_APPEND_PROMPT + FUNCTION_CALL_ENFORCEMENT_PROMPT;
    }

    private String buildSource(RetrievedChunk chunk) {
        String title = defaultText(chunk.title(), "未命名资料");
        String docId = defaultText(chunk.docId(), "未知");
        return String.format(Locale.ROOT, "%s（%s）", title, docId);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String buildPreview(String content) {
        String normalized = normalizeAnswer(content);
        if (normalized.length() <= CHUNK_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.strip();
    }

    /**
     * 定义 function call 可用的本地工具。
     */
    private String abbreviateForLog(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= LOG_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, LOG_PREVIEW_LENGTH) + "...";
    }

    private String summarizeToolCalls(List<SiliconFlowChatDemoService.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "[]";
        }

        List<String> summaries = new ArrayList<>(toolCalls.size());
        for (SiliconFlowChatDemoService.ToolCall toolCall : toolCalls) {
            summaries.add(toolCall.name() + "(arguments=" + abbreviateForLog(toolCall.arguments()) + ")");
        }
        return summaries.toString();
    }

    private String summarizeToolResults(List<SiliconFlowChatDemoService.ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "[]";
        }

        List<String> summaries = new ArrayList<>(toolResults.size());
        for (SiliconFlowChatDemoService.ToolResult toolResult : toolResults) {
            summaries.add(toolResult.toolName() + "(content=" + abbreviateForLog(toolResult.content()) + ")");
        }
        return summaries.toString();
    }

    private ArrayNode buildFunctionTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(buildListChunksTool());
        tools.add(buildChunkDetailTool());
        return tools;
    }

    private ObjectNode buildListChunksTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "listRetrievedChunks");
        function.put("description", "查看本次已召回资料的目录、编号、标题、分类和摘要");
        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");
        parameters.putObject("properties");
        parameters.putArray("required");
        return tool;
    }

    private ObjectNode buildChunkDetailTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", "getRetrievedChunkByIndex");
        function.put("description", "按资料编号查看某条已召回资料的完整内容");
        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode index = properties.putObject("index");
        index.put("type", "integer");
        index.put("description", "资料编号，从 1 开始");
        ArrayNode required = parameters.putArray("required");
        required.add("index");
        return tool;
    }

    /**
     * 执行模型要求调用的本地工具，并将结果序列化成 tool message。
     */
    private List<SiliconFlowChatDemoService.ToolResult> executeToolCalls(
            List<SiliconFlowChatDemoService.ToolCall> toolCalls,
            List<RetrievedChunk> chunks
    ) {
        List<SiliconFlowChatDemoService.ToolResult> results = new ArrayList<>(toolCalls.size());
        for (SiliconFlowChatDemoService.ToolCall toolCall : toolCalls) {
            String result = switch (toolCall.name()) {
                case "listRetrievedChunks" -> serializeToolResult(buildChunkCatalogPayload(chunks));
                case "getRetrievedChunkByIndex" -> serializeToolResult(buildChunkDetailPayload(toolCall.arguments(), chunks));
                default -> serializeToolResult(Map.of("error", "未知工具：" + toolCall.name()));
            };
            log.info(
                    "RAG function call 执行工具: toolName={}, arguments={}, result={}",
                    toolCall.name(),
                    abbreviateForLog(toolCall.arguments()),
                    abbreviateForLog(result)
            );
            results.add(new SiliconFlowChatDemoService.ToolResult(toolCall.id(), toolCall.name(), result));
        }
        return results;
    }

    private Map<String, Object> buildChunkCatalogPayload(List<RetrievedChunk> chunks) {
        List<Map<String, Object>> items = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index + 1);
            item.put("title", defaultText(chunk.title(), "未命名资料"));
            item.put("docId", defaultText(chunk.docId(), "未知"));
            item.put("category", defaultText(chunk.category(), "general"));
            item.put("preview", buildPreview(chunk.content()));
            items.add(item);
        }
        return Map.of("chunks", items);
    }

    private Map<String, Object> buildChunkDetailPayload(String arguments, List<RetrievedChunk> chunks) {
        try {
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            int index = Integer.parseInt(String.valueOf(args.getOrDefault("index", "0")));
            if (index < 1 || index > chunks.size()) {
                return Map.of("error", "资料编号超出范围", "requestedIndex", index);
            }

            RetrievedChunk chunk = chunks.get(index - 1);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("index", index);
            payload.put("title", defaultText(chunk.title(), "未命名资料"));
            payload.put("docId", defaultText(chunk.docId(), "未知"));
            payload.put("category", defaultText(chunk.category(), "general"));
            payload.put("sourceUrl", defaultText(chunk.sourceUrl(), ""));
            payload.put("content", chunk.content());
            return payload;
        } catch (Exception exception) {
            return Map.of("error", "工具参数解析失败", "arguments", arguments);
        }
    }

    private String serializeToolResult(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 function call 工具结果失败", exception);
        }
    }

    record RetrievedChunk(int rank, String docId, String title, String category, String sourceUrl, String content) {
    }
}
