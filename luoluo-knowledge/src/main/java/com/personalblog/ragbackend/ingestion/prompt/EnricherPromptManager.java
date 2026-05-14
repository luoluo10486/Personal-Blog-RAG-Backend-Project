package com.personalblog.ragbackend.ingestion.prompt;

import com.personalblog.ragbackend.ingestion.domain.enums.ChunkEnrichType;

import java.util.EnumMap;
import java.util.Map;

public final class EnricherPromptManager {

    private static final Map<ChunkEnrichType, String> DEFAULT_SYSTEM_PROMPTS = new EnumMap<>(ChunkEnrichType.class);

    static {
        DEFAULT_SYSTEM_PROMPTS.put(ChunkEnrichType.KEYWORDS, "从文档片段中提取 3-8 个关键词，只输出 JSON 数组。");
        DEFAULT_SYSTEM_PROMPTS.put(ChunkEnrichType.SUMMARY, "请用 1-3 句话概括文本片段，直接输出摘要文本。");
        DEFAULT_SYSTEM_PROMPTS.put(ChunkEnrichType.METADATA, "从文本片段中抽取可结构化的信息，只输出 JSON 对象。");
    }

    private EnricherPromptManager() {
    }

    public static String systemPrompt(ChunkEnrichType type) {
        return DEFAULT_SYSTEM_PROMPTS.get(type);
    }
}
