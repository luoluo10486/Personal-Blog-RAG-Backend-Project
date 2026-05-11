package com.personalblog.ragbackend.rag.core.intent;

import java.util.List;

public record IntentGroup(
        List<NodeScore> mcpIntents,
        List<NodeScore> kbIntents
) {
}
