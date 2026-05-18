package com.personalblog.ragbackend.mcp.config;

import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import com.personalblog.ragbackend.rag.core.retrieve.PgRetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class McpRetrieverConfiguration {

    @Bean
    public RetrieverService retrieverService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        return new PgRetrieverService(jdbcTemplate, embeddingService);
    }
}
