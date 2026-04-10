package com.personalblog.ragbackend.mcp;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
        scanBasePackages = {
                "com.personalblog.ragbackend.service",
                "com.personalblog.ragbackend.rag.config",
                "com.personalblog.ragbackend.mcp"
        },
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = "com.personalblog.ragbackend.rag.config")
public class LuoluoRagMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuoluoRagMcpApplication.class, args);
    }
}
