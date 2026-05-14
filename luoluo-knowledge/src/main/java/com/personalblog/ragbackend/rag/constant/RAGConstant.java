package com.personalblog.ragbackend.rag.constant;

public class RAGConstant {
    public static final int DEFAULT_TOP_K = 10;
    public static final String MULTI_CHANNEL_KEY = "multi_channel";

    public static final String CHAT_SYSTEM_PROMPT_PATH = "prompt/answer-chat-system.st";
    public static final String RAG_ENTERPRISE_PROMPT_PATH = "prompt/answer-chat-kb.st";
    public static final String MCP_ONLY_PROMPT_PATH = "prompt/answer-chat-mcp.st";
    public static final String MCP_KB_MIXED_PROMPT_PATH = "prompt/answer-chat-mcp-kb-mixed.st";

    private RAGConstant() {
    }
}
