package com.personalblog.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.personalblog.ragbackend.common",
        "com.personalblog.ragbackend.member"
})
@ConfigurationPropertiesScan(basePackages = "com.personalblog.ragbackend.member.config")
@MapperScan({
        "com.personalblog.ragbackend.member.mapper",
        "com.personalblog.ragbackend.common.auth.mapper"
})
public class LuoluoMemberTestApplication {
}
