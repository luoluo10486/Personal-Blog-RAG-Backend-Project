package com.personalblog.ragbackend.rag.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置。
 */
@Configuration
public class MilvusClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.milvus", name = "enabled", havingValue = "true")
    public MilvusClientV2 milvusClientV2(RagProperties ragProperties) {
        RagProperties.MilvusProperties milvusProperties = ragProperties.getMilvus();

        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .connectTimeoutMs(ragProperties.getConnectTimeoutSeconds() * 1000L);
        if (milvusProperties.getToken() != null && !milvusProperties.getToken().isBlank()) {
            builder.token(milvusProperties.getToken().trim());
        }
        if (milvusProperties.getDatabaseName() != null && !milvusProperties.getDatabaseName().isBlank()) {
            builder.dbName(milvusProperties.getDatabaseName().trim());
        }
        return new MilvusClientV2(builder.build());
    }
}
