package com.personalblog.ragbackend.rag.core.intent;

import java.util.List;

public record SubQuestionIntent(
        String subQuestion,
        List<NodeScore> nodeScores
) {
}
