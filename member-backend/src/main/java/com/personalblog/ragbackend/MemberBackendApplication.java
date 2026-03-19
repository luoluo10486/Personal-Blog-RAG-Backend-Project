package com.personalblog.ragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MemberBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberBackendApplication.class, args);
    }
}
