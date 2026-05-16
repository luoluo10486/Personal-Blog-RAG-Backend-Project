package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
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
    public String formatMcpContext(Map<String, List<CallToolResult>> toolResults, List<NodeScore> mcpIntents) {
        if (CollUtil.isEmpty(toolResults)) {
            return "";
        }
        if (CollUtil.isEmpty(mcpIntents)) {
            return mergeResultsToText(toolResults.values().stream().flatMap(List::stream).toList());
        }

        Map<String, IntentNode> toolToIntent = new LinkedHashMap<>();
        for (NodeScore ns : mcpIntents) {
            IntentNode node = ns == null ? null : ns.node();
            if (node == null || StrUtil.isBlank(node.getMcpToolId())) {
                continue;
            }
            toolToIntent.putIfAbsent(node.getMcpToolId(), node);
        }

        return toolToIntent.entrySet().stream()
                .map(entry -> {
                    List<CallToolResult> results = toolResults.get(entry.getKey());
                    if (CollUtil.isEmpty(results)) {
                        return "";
                    }
                    IntentNode node = entry.getValue();
                    String snippet = StrUtil.emptyIfNull(node.getPromptSnippet()).trim();
                    String body = mergeResultsToText(results);
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

    private String mergeResultsToText(List<CallToolResult> results) {
        if (CollUtil.isEmpty(results)) {
            return "";
        }

        List<String> successTexts = new ArrayList<>();
        List<String> errorTexts = new ArrayList<>();
        for (CallToolResult result : results) {
            boolean isError = result.isError() != null && result.isError();
            String text = extractTextContent(result);
            if (!isError && StrUtil.isNotBlank(text)) {
                successTexts.add(text.trim());
            } else if (isError && StrUtil.isNotBlank(text)) {
                errorTexts.add("- 宸ュ叿璋冪敤澶辫触: " + text.trim());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String text : successTexts) {
            sb.append(text).append("\n\n");
        }
        if (CollUtil.isNotEmpty(errorTexts)) {
            String errorList = String.join("\n", errorTexts);
            sb.append(promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, "mcp-error", Map.of(
                    "error_list", errorList
            )));
        }
        return sb.toString().trim();
    }

    private String extractTextContent(CallToolResult result) {
        if (result == null || result.content() == null) {
            return null;
        }
        List<String> texts = result.content().stream()
                .filter(content -> content instanceof TextContent)
                .map(content -> ((TextContent) content).text())
                .filter(StrUtil::isNotBlank)
                .toList();
        return texts.isEmpty() ? null : String.join("\n", texts);
    }
}
