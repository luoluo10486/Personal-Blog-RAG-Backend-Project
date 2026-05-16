package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.List;
import java.util.Map;

public interface ContextFormatter {
    String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK);

    String formatMcpContext(Map<String, List<CallToolResult>> toolResults, List<NodeScore> mcpIntents);
}
