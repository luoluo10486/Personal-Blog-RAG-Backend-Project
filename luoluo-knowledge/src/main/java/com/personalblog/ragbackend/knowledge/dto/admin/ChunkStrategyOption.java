package com.personalblog.ragbackend.knowledge.dto.admin;

import java.util.Map;

public class ChunkStrategyOption {
    private String value;
    private String label;
    private Map<String, Integer> defaultConfig;

    public ChunkStrategyOption() {
    }

    public ChunkStrategyOption(String value, String label, Map<String, Integer> defaultConfig) {
        this.value = value;
        this.label = label;
        this.defaultConfig = defaultConfig;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Map<String, Integer> getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(Map<String, Integer> defaultConfig) {
        this.defaultConfig = defaultConfig;
    }
}
