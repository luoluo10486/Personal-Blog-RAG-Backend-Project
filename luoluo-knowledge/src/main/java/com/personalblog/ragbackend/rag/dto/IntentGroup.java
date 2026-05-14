package com.personalblog.ragbackend.rag.dto;

import com.personalblog.ragbackend.rag.core.intent.NodeScore;

import java.util.List;

public record IntentGroup(List<NodeScore> mcpIntents, List<NodeScore> kbIntents) {
}
