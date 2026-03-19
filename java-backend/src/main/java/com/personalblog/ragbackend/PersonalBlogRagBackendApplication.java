package com.personalblog.ragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PersonalBlogRagBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalBlogRagBackendApplication.class, args);
    }
}
