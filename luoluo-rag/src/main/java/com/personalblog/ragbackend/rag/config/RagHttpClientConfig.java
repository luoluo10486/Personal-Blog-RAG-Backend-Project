package com.personalblog.ragbackend.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RAG 模块 HTTP 客户端配置，统一设置模型请求的连接超时。
 */
@Configuration
public class RagHttpClientConfig {

    /**
     * 注册专用于 RAG 调用的 HttpClient。
     */
    @Bean
    public HttpClient ragHttpClient(RagProperties ragProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ragProperties.getConnectTimeoutSeconds()))
                .build();
    }
}
