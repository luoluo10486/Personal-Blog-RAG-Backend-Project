package com.personalblog.ragbackend.ingestion.util;

import java.util.Map;

public final class PromptTemplateRenderer {

    private PromptTemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> values) {
        if (template == null || template.isBlank() || values == null || values.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace(key, value);
        }
        return rendered;
    }
}
