package com.personalblog.ragbackend.knowledge.dto.rag;

import java.util.List;

public record RagTraceDetailView(
        RagTraceRunView run,
        List<RagTraceNodeView> nodes
) {
}
