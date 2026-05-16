package com.personalblog.ragbackend.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RAGConfigProperties {
    @Value("${rag.query-rewrite.enabled:true}")
    private Boolean queryRewriteEnabled;

    @Value("${rag.query-rewrite.max-history-messages:4}")
    private Integer queryRewriteMaxHistoryMessages;

    @Value("${rag.query-rewrite.max-history-chars:200}")
    private Integer queryRewriteMaxHistoryChars;

    public Boolean getQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public Integer getQueryRewriteMaxHistoryMessages() {
        return queryRewriteMaxHistoryMessages;
    }

    public Integer getQueryRewriteMaxHistoryChars() {
        return queryRewriteMaxHistoryChars;
    }
}
