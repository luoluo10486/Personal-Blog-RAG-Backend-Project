package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.mcp.MCPResponse;

import java.util.List;
import java.util.Map;

public interface ContextFormatter {
    String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK);

    String formatMcpContext(List<MCPResponse> responses, List<NodeScore> mcpIntents);
}
