package com.personalblog.ragbackend.knowledge.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.service.rag.intent.RagIntentNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeContextFormatter {
    private static final int MAX_KB_CONTEXT_CHARS = 6000;
    private static final int MAX_MCP_CONTEXT_CHARS = 4000;

    public String formatKbContext(List<NodeScore> kbIntents, List<KnowledgeChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        List<String> snippets = collectPromptSnippets(kbIntents);
        if (!snippets.isEmpty()) {
            builder.append("### 回答规则\n");
            for (int i = 0; i < snippets.size(); i++) {
                builder.append(i + 1).append(". ").append(snippets.get(i)).append("\n");
            }
            builder.append("\n");
        }
        builder.append("### 文档内容\n");
        int usedChars = 0;
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            String content = safeTruncate(chunk.content(), 1200);
            usedChars += content.length();
            if (usedChars > MAX_KB_CONTEXT_CHARS) {
                break;
            }
            builder.append("[K").append(index + 1).append("] ")
                    .append(StrUtil.blankToDefault(chunk.title(), ""))
                    .append(" / chunk ").append(chunk.chunkIndex())
                    .append(" / score ").append(String.format("%.4f", chunk.score()))
                    .append("\n")
                    .append(content)
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    public String formatMcpContext(String mcpContext) {
        if (StrUtil.isBlank(mcpContext)) {
            return "";
        }
        return "### 动态数据片段\n" + safeTruncate(mcpContext, MAX_MCP_CONTEXT_CHARS);
    }

    private List<String> collectPromptSnippets(List<NodeScore> kbIntents) {
        if (CollUtil.isEmpty(kbIntents)) {
            return List.of();
        }
        Set<String> snippets = new LinkedHashSet<>();
        for (NodeScore score : kbIntents) {
            RagIntentNode node = score == null ? null : score.node();
            if (node == null || StrUtil.isBlank(node.promptSnippet)) {
                continue;
            }
            snippets.add(node.promptSnippet.trim());
        }
        return snippets.stream().collect(Collectors.toList());
    }

    private String safeTruncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
