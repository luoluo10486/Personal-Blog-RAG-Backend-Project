package com.personalblog.ragbackend.infra.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
public class AIHttpClientConfig {

    @Bean
    public HttpClient aiHttpClient(AIModelProperties aiProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(aiProperties.getConnectTimeoutSeconds()))
                .build();
    }

    @Bean
    @Qualifier("aiStreamExecutor")
    public Executor aiStreamExecutor(AIModelProperties aiProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = aiProperties.getStream().getExecutorThreads();
        executor.setThreadNamePrefix("infra-ai-stream-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(256);
        executor.initialize();
        return executor;
    }
}
