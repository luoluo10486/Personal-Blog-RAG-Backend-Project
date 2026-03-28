package com.personalblog.ragbackend.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RagHttpClientConfig {

    @Bean
    public HttpClient ragHttpClient(RagProperties ragProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ragProperties.getConnectTimeoutSeconds()))
                .build();
    }
}
