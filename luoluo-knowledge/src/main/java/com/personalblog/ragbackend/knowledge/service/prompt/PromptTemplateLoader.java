package com.personalblog.ragbackend.knowledge.service.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class PromptTemplateLoader {
    public String load(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load prompt template: " + classpathLocation, exception);
        }
    }
}
