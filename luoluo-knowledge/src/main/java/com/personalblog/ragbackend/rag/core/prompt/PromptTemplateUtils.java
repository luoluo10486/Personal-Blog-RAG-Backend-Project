package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.util.StrUtil;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public final class PromptTemplateUtils {
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("(\\n){3,}");

    private PromptTemplateUtils() {
    }

    public static String cleanupPrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return MULTI_BLANK_LINES.matcher(prompt).replaceAll("\n\n").trim();
    }

    public static String fillSlots(String template, Map<String, String> slots) {
        if (template == null) {
            return "";
        }
        if (slots == null || slots.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            String value = StrUtil.emptyIfNull(entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    public static Map<String, String> parseSections(String template) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (StrUtil.isBlank(template)) {
            return sections;
        }

        String currentSection = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : template.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--- section:")) {
                if (currentSection != null) {
                    sections.put(currentSection, buffer.toString().trim());
                    buffer.setLength(0);
                }
                currentSection = trimmed.substring("--- section:".length()).replace("---", "").trim();
                continue;
            }
            if (currentSection != null) {
                buffer.append(line).append('\n');
            }
        }
        if (currentSection != null) {
            sections.put(currentSection, buffer.toString().trim());
        }
        return sections;
    }
}
