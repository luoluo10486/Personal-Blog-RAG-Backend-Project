package com.personalblog.ragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

/**
 * MemberBackendApplication 是应用启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.personalblog.ragbackend.member.mapper")
public class MemberBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberBackendApplication.class, args);
    }
}

