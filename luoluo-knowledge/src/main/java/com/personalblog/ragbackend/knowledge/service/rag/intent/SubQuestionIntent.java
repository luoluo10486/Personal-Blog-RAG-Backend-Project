package com.personalblog.ragbackend.knowledge.service.rag.intent;

import java.util.List;

public record SubQuestionIntent(
        String subQuestion,
        List<NodeScore> nodeScores
) {
}
