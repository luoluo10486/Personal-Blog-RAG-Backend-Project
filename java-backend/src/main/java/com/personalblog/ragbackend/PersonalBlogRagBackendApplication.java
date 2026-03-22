package com.personalblog.ragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PersonalBlogRagBackendApplication 是应用启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PersonalBlogRagBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalBlogRagBackendApplication.class, args);
    }
}

