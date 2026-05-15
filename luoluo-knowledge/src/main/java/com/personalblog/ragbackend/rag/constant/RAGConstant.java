package com.personalblog.ragbackend.rag.constant;

public class RAGConstant {
    public static final int DEFAULT_TOP_K = 10;
    public static final String MULTI_CHANNEL_KEY = "multi_channel";
    public static final double INTENT_MIN_SCORE = 0.35D;
    public static final int MAX_INTENT_COUNT = 3;

    public static final String CHAT_SYSTEM_PROMPT_PATH = "prompt/answer-chat-system.st";
    public static final String RAG_ENTERPRISE_PROMPT_PATH = "prompt/answer-chat-kb.st";
    public static final String MCP_ONLY_PROMPT_PATH = "prompt/answer-chat-mcp.st";
    public static final String MCP_KB_MIXED_PROMPT_PATH = "prompt/answer-chat-mcp-kb-mixed.st";
    public static final String GUIDANCE_PROMPT_PATH = "prompt/guidance-prompt.st";
    public static final String GUIDANCE_AMBIGUITY_CHECK_PROMPT_PATH = "prompt/guidance-ambiguity-check.st";
    public static final String QUERY_REWRITE_AND_SPLIT_PROMPT_PATH = "prompt/user-question-rewrite.st";
    public static final String INTENT_CLASSIFIER_PROMPT_PATH = "prompt/intent-classifier.st";

    private RAGConstant() {
    }
}
