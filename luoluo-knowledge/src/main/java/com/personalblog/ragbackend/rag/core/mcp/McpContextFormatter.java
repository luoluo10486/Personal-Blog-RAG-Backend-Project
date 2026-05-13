package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.intent.RagIntentNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpContextFormatter {

    public String format(List<MCPResponse> responses, List<NodeScore> mcpIntents) {
        if (CollUtil.isEmpty(responses) || responses.stream().noneMatch(MCPResponse::isSuccess)) {
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

        Map<String, List<MCPResponse>> grouped = responses.stream()
                .filter(MCPResponse::isSuccess)
                .filter(result -> StrUtil.isNotBlank(result.getToolId()))
                .collect(Collectors.groupingBy(MCPResponse::getToolId, LinkedHashMap::new, Collectors.toList()));

        return toolToIntent.entrySet().stream()
                .map(entry -> {
                    List<MCPResponse> toolResponses = grouped.get(entry.getKey());
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

    private String mergeResponsesToText(List<MCPResponse> responses) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (MCPResponse response : responses) {
            if (response.isSuccess() && StrUtil.isNotBlank(response.getTextResult())) {
                builder.append("[M").append(index++).append("]\n")
                        .append(response.getTextResult().trim())
                        .append("\n\n");
            }
        }
        return builder.toString().trim();
    }
}
