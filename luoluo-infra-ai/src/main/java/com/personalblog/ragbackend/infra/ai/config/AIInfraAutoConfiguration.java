package com.personalblog.ragbackend.infra.ai.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(AIModelProperties.class)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.personalblog.ragbackend.infra.ai")
public class AIInfraAutoConfiguration {
}
