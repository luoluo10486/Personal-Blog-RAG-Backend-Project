package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class PromptPlan {
    private List<NodeScore> retainedIntents;
    private String baseTemplate;
}
