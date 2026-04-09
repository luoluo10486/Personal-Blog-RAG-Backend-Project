package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResult;
import com.personalblog.ragbackend.dto.rag.RagGenerationCitation;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private static final String FALLBACK_ANSWER = "根据现有资料，暂时无法回答该问题。建议您联系人工客服获取更多帮助。";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
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

    private final SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;
    private final SiliconFlowChatDemoService siliconFlowChatDemoService;

    public RagGenerationDemoService(
            SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService,
            SiliconFlowChatDemoService siliconFlowChatDemoService
    ) {
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
                    List.of()
            );
        }

        String context = buildContext(chunks);
        String systemPrompt = resolveSystemPrompt(request.systemPrompt());
        RagDemoChatResponse chatResponse = siliconFlowChatDemoService.chat(
                new RagDemoChatRequest(systemPrompt, buildUserMessage(context, query))
        );
        List<RagGenerationCitation> citations = parseCitations(chatResponse.answer(), chunks);

        return new RagGenerationResponse(
                query,
                chatResponse.answer(),
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

    private String resolveSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return GENERATION_SYSTEM_PROMPT;
        }
        return systemPrompt.trim();
    }

    private String buildSource(RetrievedChunk chunk) {
        String title = defaultText(chunk.title(), "未命名资料");
        String docId = defaultText(chunk.docId(), "未知");
        return String.format(Locale.ROOT, "%s（%s）", title, docId);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    record RetrievedChunk(int rank, String docId, String title, String category, String sourceUrl, String content) {
    }
}
