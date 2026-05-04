package com.personalblog.ragbackend.knowledge.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeMilvusClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.knowledge.vector.milvus", name = "enabled", havingValue = "true")
    public MilvusClientV2 knowledgeMilvusClient(KnowledgeProperties knowledgeProperties) {
        KnowledgeProperties.MilvusProperties milvusProperties = knowledgeProperties.getVector().getMilvus();
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .connectTimeoutMs(30000L);
        if (milvusProperties.getToken() != null && !milvusProperties.getToken().isBlank()) {
            builder.token(milvusProperties.getToken().trim());
        }
        if (milvusProperties.getDatabaseName() != null && !milvusProperties.getDatabaseName().isBlank()) {
            builder.dbName(milvusProperties.getDatabaseName().trim());
        }
        return new MilvusClientV2(builder.build());
    }
}
