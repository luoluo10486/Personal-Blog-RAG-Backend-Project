package com.personalblog.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 会员后端应用启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({
        "com.personalblog.ragbackend.member.mapper",
        "com.personalblog.ragbackend.common.auth.mapper"
})
public class MemberBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberBackendApplication.class, args);
    }
}
