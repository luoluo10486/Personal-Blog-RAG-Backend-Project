package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.service.rag.intent.RagIntentNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpContextFormatter {

    public String format(List<McpToolCallResult> responses, List<NodeScore> mcpIntents) {
        if (CollUtil.isEmpty(responses) || responses.stream().noneMatch(McpToolCallResult::success)) {
            return "";
        }
        if (CollUtil.isEmpty(mcpIntents)) {
            return mergeResponsesToText(responses);
        }

        Map<String, RagIntentNode> toolToIntent = new LinkedHashMap<>();
        for (NodeScore score : mcpIntents) {
            RagIntentNode node = score.node();
            if (node == null || StrUtil.isBlank(node.mcpToolId)) {
                continue;
            }
            toolToIntent.putIfAbsent(node.mcpToolId, node);
        }

        Map<String, List<McpToolCallResult>> grouped = responses.stream()
                .filter(McpToolCallResult::success)
                .filter(result -> StrUtil.isNotBlank(result.toolId()))
                .collect(Collectors.groupingBy(McpToolCallResult::toolId, LinkedHashMap::new, Collectors.toList()));

        return toolToIntent.entrySet().stream()
                .map(entry -> {
                    List<McpToolCallResult> toolResponses = grouped.get(entry.getKey());
                    if (CollUtil.isEmpty(toolResponses)) {
                        return "";
                    }
                    RagIntentNode node = entry.getValue();
                    StringBuilder builder = new StringBuilder();
                    if (StrUtil.isNotBlank(node.promptSnippet)) {
                        builder.append("Intent rule:\n").append(node.promptSnippet.trim()).append("\n\n");
                    }
                    builder.append("Dynamic tool evidence:\n").append(mergeResponsesToText(toolResponses));
                    return builder.toString().trim();
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n\n"));
    }

    private String mergeResponsesToText(List<McpToolCallResult> responses) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (McpToolCallResult response : responses) {
            if (response.success() && StrUtil.isNotBlank(response.text())) {
                builder.append("[M").append(index++).append("]\n")
                        .append(response.text().trim())
                        .append("\n\n");
            }
        }
        return builder.toString().trim();
    }
}
