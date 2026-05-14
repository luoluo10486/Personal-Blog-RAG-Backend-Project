package com.personalblog.ragbackend.ingestion.prompt;

import com.personalblog.ragbackend.ingestion.domain.enums.EnhanceType;

import java.util.EnumMap;
import java.util.Map;

public final class EnhancerPromptManager {

    private static final Map<EnhanceType, String> DEFAULT_SYSTEM_PROMPTS = new EnumMap<>(EnhanceType.class);

    static {
        DEFAULT_SYSTEM_PROMPTS.put(EnhanceType.CONTEXT_ENHANCE, "你是文档整理专家。请直接输出整理后的文本。");
        DEFAULT_SYSTEM_PROMPTS.put(EnhanceType.KEYWORDS, "从文本中提取 5-15 个关键短语，只输出 JSON 数组。");
        DEFAULT_SYSTEM_PROMPTS.put(EnhanceType.QUESTIONS, "根据文本生成 3-5 个问题，只输出 JSON 数组。");
        DEFAULT_SYSTEM_PROMPTS.put(EnhanceType.METADATA, "从文本中提取结构化信息，只输出 JSON 对象。");
    }

    private EnhancerPromptManager() {
    }

    public static String systemPrompt(EnhanceType type) {
        return DEFAULT_SYSTEM_PROMPTS.get(type);
    }
}
