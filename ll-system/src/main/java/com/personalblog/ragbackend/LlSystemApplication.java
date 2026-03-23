package com.personalblog.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 公共系统接口应用启动类，负责装配公共接口层所需的会员业务能力与公共基础设施。
 */
@SpringBootApplication(scanBasePackages = {
        "com.personalblog.ragbackend.common",
        "com.personalblog.ragbackend.config",
        "com.personalblog.ragbackend.member.application",
        "com.personalblog.ragbackend.member.service",
        "com.personalblog.ragbackend.system"
})
@ConfigurationPropertiesScan(basePackages = "com.personalblog.ragbackend.config")
@MapperScan({
        "com.personalblog.ragbackend.member.mapper",
        "com.personalblog.ragbackend.common.auth.mapper"
})
public class LlSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlSystemApplication.class, args);
    }
}
