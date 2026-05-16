package com.personalblog.ragbackend.knowledge.service.prompt;

import cn.hutool.core.util.StrUtil;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public String renderSection(String classpathLocation, String sectionName, Map<String, String> values) {
        String template = load(classpathLocation);
        if (StrUtil.isBlank(template) || StrUtil.isBlank(sectionName)) {
            return "";
        }
        List<String> sectionLines = extractSectionLines(template, sectionName);
        if (sectionLines.isEmpty()) {
            return "";
        }
        return renderInline(String.join("\n", sectionLines), values);
    }

    private List<String> extractSectionLines(String template, String sectionName) {
        String targetHeader = "--- section: " + sectionName;
        String[] lines = template.split("\\R");
        List<String> sectionLines = new ArrayList<>();
        boolean inSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--- section:")) {
                if (inSection) {
                    break;
                }
                inSection = trimmed.equals(targetHeader);
                continue;
            }
            if (inSection) {
                sectionLines.add(line);
            }
        }
        return sectionLines;
    }

    private String renderInline(String template, Map<String, String> values) {
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
