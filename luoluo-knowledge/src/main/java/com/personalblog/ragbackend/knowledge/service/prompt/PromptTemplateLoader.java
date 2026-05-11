package com.personalblog.ragbackend.knowledge.service.prompt;

import cn.hutool.core.util.StrUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    public String render(String classpathLocation, Map<String, String> values) {
        String template = load(classpathLocation);
        if (values == null || values.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", StrUtil.blankToDefault(entry.getValue(), ""));
        }
        return rendered;
    }
}
