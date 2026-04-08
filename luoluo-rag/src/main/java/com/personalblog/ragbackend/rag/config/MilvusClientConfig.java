package com.personalblog.ragbackend.rag.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

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
        try {
            return new MilvusClientV2(builder.build());
        } catch (RuntimeException exception) {
            String databaseName = milvusProperties.getDatabaseName();
            String databaseLabel = (databaseName == null || databaseName.isBlank()) ? "默认库" : databaseName.trim();
            throw new IllegalStateException(
                    "Milvus 客户端初始化失败，请检查 Milvus 服务是否已启动，以及连接地址是否可用。"
                            + " 当前地址: " + Objects.toString(milvusProperties.getUri(), "")
                            + "，数据库: " + databaseLabel,
                    exception
            );
        }
    }
}
