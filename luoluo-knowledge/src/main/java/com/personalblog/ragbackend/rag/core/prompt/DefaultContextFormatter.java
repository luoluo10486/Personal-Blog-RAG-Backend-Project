package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.mcp.MCPResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
public class DefaultContextFormatter implements ContextFormatter {
    private final PromptTemplateLoader promptTemplateLoader;

    public DefaultContextFormatter(PromptTemplateLoader promptTemplateLoader) {
        this.promptTemplateLoader = promptTemplateLoader;
    }

    @Override
    public String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        if (rerankedByIntent == null || rerankedByIntent.isEmpty()) {
            return "";
        }
        if (CollUtil.isEmpty(kbIntents)) {
            return formatChunksWithoutIntent(rerankedByIntent, topK);
        }
        if (kbIntents.size() > 1) {
            return formatMultiIntentContext(kbIntents, rerankedByIntent, topK);
        }
        return formatSingleIntentContext(kbIntents.get(0), rerankedByIntent, topK);
    }

    @Override
    public String formatMcpContext(List<MCPResponse> responses, List<NodeScore> mcpIntents) {
        if (CollUtil.isEmpty(responses) || responses.stream().noneMatch(MCPResponse::isSuccess)) {
            return "";
        }
        if (CollUtil.isEmpty(mcpIntents)) {
            return mergeResponsesToText(responses);
        }

        Map<String, IntentNode> toolToIntent = new LinkedHashMap<>();
        for (NodeScore ns : mcpIntents) {
            IntentNode node = ns == null ? null : ns.node();
            if (node == null || StrUtil.isBlank(node.getMcpToolId())) {
                continue;
            }
            toolToIntent.putIfAbsent(node.getMcpToolId(), node);
        }

        Map<String, List<MCPResponse>> grouped = responses.stream()
                .filter(MCPResponse::isSuccess)
                .filter(response -> StrUtil.isNotBlank(response.getToolId()))
                .collect(Collectors.groupingBy(MCPResponse::getToolId, LinkedHashMap::new, Collectors.toList()));

        return toolToIntent.entrySet().stream()
                .map(entry -> {
                    List<MCPResponse> toolResponses = grouped.get(entry.getKey());
                    if (CollUtil.isEmpty(toolResponses)) {
                        return "";
                    }
                    IntentNode node = entry.getValue();
                    String snippet = StrUtil.emptyIfNull(node.getPromptSnippet()).trim();
                    String body = mergeResponsesToText(toolResponses);
                    if (StrUtil.isBlank(body)) {
                        return "";
                    }
                    String snippetSection = renderSnippetRules(snippet);
                    return renderMcpSection(snippetSection, body);
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatSingleIntentContext(NodeScore nodeScore,
                                             Map<String, List<RetrievedChunk>> rerankedByIntent,
                                             int topK) {
        if (nodeScore == null || nodeScore.node() == null) {
            return "";
        }
        List<RetrievedChunk> chunks = rerankedByIntent.get(nodeScore.node().getId());
        if (CollUtil.isEmpty(chunks)) {
            return "";
        }
        String snippet = StrUtil.emptyIfNull(nodeScore.node().getPromptSnippet()).trim();
        String body = joinChunkTexts(chunks, topK);
        return renderKbSection(renderSnippetRules(snippet), body);
    }

    private String formatMultiIntentContext(List<NodeScore> kbIntents,
                                            Map<String, List<RetrievedChunk>> rerankedByIntent,
                                            int topK) {
        List<String> snippets = kbIntents.stream()
                .map(NodeScore::node)
                .filter(node -> node != null && StrUtil.isNotBlank(node.getPromptSnippet()))
                .map(IntentNode::getPromptSnippet)
                .map(String::trim)
                .distinct()
                .toList();

        String snippetSection = "";
        if (!snippets.isEmpty()) {
            String numberedRules = IntStream.range(0, snippets.size())
                    .mapToObj(index -> (index + 1) + ". " + snippets.get(index))
                    .collect(Collectors.joining("\n"));
            snippetSection = renderSnippetRules(numberedRules);
        }

        List<RetrievedChunk> allChunks = rerankedByIntent.values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .distinct()
                .limit(topK)
                .toList();
        if (allChunks.isEmpty()) {
            return snippetSection;
        }

        return renderKbSection(snippetSection, joinChunkTexts(allChunks, topK));
    }

    private String formatChunksWithoutIntent(Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        int limit = topK > 0 ? topK : Integer.MAX_VALUE;
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (List<RetrievedChunk> list : rerankedByIntent.values()) {
            if (CollUtil.isEmpty(list)) {
                continue;
            }
            for (RetrievedChunk chunk : list) {
                chunks.add(chunk);
                if (chunks.size() >= limit) {
                    break;
                }
            }
            if (chunks.size() >= limit) {
                break;
            }
        }
        if (chunks.isEmpty()) {
            return "";
        }
        return renderKbSection("", joinChunkTexts(chunks, topK));
    }

    private String renderKbSection(String snippetSection, String chunksBody) {
        return promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, "kb-section", Map.of(
                "snippet_section", StrUtil.blankToDefault(snippetSection, ""),
                "chunks_body", StrUtil.blankToDefault(chunksBody, "")
        ));
    }

    private String renderSnippetRules(String rules) {
        if (StrUtil.isBlank(rules)) {
            return "";
        }
        return promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, "snippet-rules", Map.of(
                "rules", rules
        ));
    }

    private String renderMcpSection(String snippetSection, String body) {
        return promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, "mcp-section", Map.of(
                "snippet_section", StrUtil.blankToDefault(snippetSection, ""),
                "body", StrUtil.blankToDefault(body, "")
        ));
    }

    private String joinChunkTexts(List<RetrievedChunk> chunks, int topK) {
        return chunks.stream()
                .limit(topK)
                .map(RetrievedChunk::getText)
                .collect(Collectors.joining("\n"));
    }

    private String mergeResponsesToText(List<MCPResponse> responses) {
        List<String> successResults = new ArrayList<>();
        List<String> errorResults = new ArrayList<>();

        for (MCPResponse response : responses) {
            if (response.isSuccess() && StrUtil.isNotBlank(response.getTextResult())) {
                successResults.add(response.getTextResult().trim());
            } else if (!response.isSuccess()) {
                errorResults.add(String.format("工具 %s 调用失败: %s",
                        response.getToolId(), response.getErrorMessage()));
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!successResults.isEmpty()) {
            for (String result : successResults) {
                sb.append(result).append("\n\n");
            }
        }
        if (!errorResults.isEmpty()) {
            String errorList = String.join("\n", errorResults.stream().map(item -> "- " + item).toList());
            sb.append(promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, "mcp-error", Map.of(
                    "error_list", errorList
            )));
        }
        return sb.toString().trim();
    }
}
