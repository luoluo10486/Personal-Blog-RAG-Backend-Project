package com.personalblog.ragbackend.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RAGConfigProperties {
    @Value("${rag.query-rewrite.enabled:true}")
    private Boolean queryRewriteEnabled;

    public Boolean getQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }
}
