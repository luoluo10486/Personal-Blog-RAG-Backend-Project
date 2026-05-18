package com.personalblog.ragbackend.mcp;

import com.personalblog.ragbackend.knowledge.config.RustfsS3Config;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
        scanBasePackages = {
                "com.personalblog.ragbackend.knowledge.service.document",
                "com.personalblog.ragbackend.knowledge.service.vector",
                "com.personalblog.ragbackend.core.chunk",
                "com.personalblog.ragbackend.core.chunk.strategy",
                "com.personalblog.ragbackend.rag.config",
                "com.personalblog.ragbackend.mcp"
        },
        exclude = {
                org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = {
        "com.personalblog.ragbackend.knowledge.config",
        "com.personalblog.ragbackend.rag.config"
})
@MapperScan({
        "com.personalblog.ragbackend.knowledge.mapper",
        "com.personalblog.ragbackend.ingestion.dao.mapper",
        "com.personalblog.ragbackend.rag.dao.mapper"
})
@Import(RustfsS3Config.class)
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
