package com.personalblog.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 后台管理端启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.personalblog.ragbackend")
@ConfigurationPropertiesScan(basePackages = {
        "com.personalblog.ragbackend.rag.config",
        "com.personalblog.ragbackend.member.config"
})
@MapperScan({
        "com.personalblog.ragbackend.member.mapper",
        "com.personalblog.ragbackend.common.auth.mapper"
})
public class LuoluoAdminApplication {
    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LuoluoAdminApplication.class, args);
    }
}
