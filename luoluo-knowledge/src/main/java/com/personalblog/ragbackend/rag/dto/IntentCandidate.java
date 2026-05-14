package com.personalblog.ragbackend.rag.dto;

import com.personalblog.ragbackend.rag.core.intent.NodeScore;

public record IntentCandidate(int subQuestionIndex, NodeScore nodeScore) {
}
