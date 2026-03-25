package com.personalblog.ragbackend;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.personalblog.ragbackend.controller",
        "com.personalblog.ragbackend.service",
        "com.personalblog.ragbackend.repository",
        "com.personalblog.ragbackend.client",
        "com.personalblog.ragbackend.rag.config"
})
@ConfigurationPropertiesScan(basePackages = "com.personalblog.ragbackend.rag.config")
public class LuoluoRagTestApplication {
}
