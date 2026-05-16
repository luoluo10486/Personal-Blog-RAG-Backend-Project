package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> sectionCache = new ConcurrentHashMap<>();

    public String load(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalArgumentException("prompt template path is blank");
        }
        return cache.computeIfAbsent(path, this::readResource);
    }

    public String render(String path, Map<String, String> slots) {
        String template = load(path);
        String filled = PromptTemplateUtils.fillSlots(template, slots);
        return PromptTemplateUtils.cleanupPrompt(filled);
    }

    public String loadSection(String path, String section) {
        Map<String, String> sections = sectionCache.computeIfAbsent(path, p -> PromptTemplateUtils.parseSections(load(p)));
        String template = sections.get(section);
        if (template == null) {
            throw new IllegalStateException("prompt template section not found: " + path + " -> " + section);
        }
        return template;
    }

    public String renderSection(String path, String section, Map<String, String> slots) {
        String template = loadSection(path, section);
        String filled = PromptTemplateUtils.fillSlots(template, slots);
        return PromptTemplateUtils.cleanupPrompt(filled);
    }

    private String readResource(String path) {
        String location = path.startsWith("classpath:") ? path : "classpath:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("prompt template not found: " + path);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.error("failed to read prompt template: {}", path, exception);
            throw new IllegalStateException("failed to read prompt template: " + path, exception);
        }
    }
}
