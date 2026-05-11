package com.personalblog.ragbackend.knowledge.service.rag.intent;

import java.util.List;

public record IntentGroup(
        List<NodeScore> mcpIntents,
        List<NodeScore> kbIntents
) {
}
