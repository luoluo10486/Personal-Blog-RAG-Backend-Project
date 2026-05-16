package com.personalblog.ragbackend.rag.core.retrieve.channel;

import com.personalblog.ragbackend.rag.core.intent.SubQuestionIntent;
import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchContext {
    private String originalQuestion;
    private String rewrittenQuestion;
    private List<String> subQuestions;
    private List<SubQuestionIntent> intents;
    private int topK;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public String getMainQuestion() {
        return StrUtil.blankToDefault(rewrittenQuestion, StrUtil.blankToDefault(originalQuestion, ""));
    }
}
