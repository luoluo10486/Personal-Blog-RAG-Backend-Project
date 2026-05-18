package com.personalblog.ragbackend.mcp;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.personalblog.ragbackend.knowledge.config.RustfsS3Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
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
                DataSourceTransactionManagerAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = {
        "com.personalblog.ragbackend.knowledge.config",
        "com.personalblog.ragbackend.rag.config"
})
@Import(RustfsS3Config.class)
public class LuoluoRagMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuoluoRagMcpApplication.class, args);
    }
}
