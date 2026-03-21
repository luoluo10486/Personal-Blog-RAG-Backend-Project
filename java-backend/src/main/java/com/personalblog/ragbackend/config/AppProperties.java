package com.personalblog.ragbackend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AppProperties 配置类，用于注册项目相关组件。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String apiPrefix = "/api/v1";
    private String blogTitle = "我的个人博客";
    private final Rag rag = new Rag();

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getBlogTitle() {
        return blogTitle;
    }

    public void setBlogTitle(String blogTitle) {
        this.blogTitle = blogTitle;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Rag {
        private boolean enabled = false;
        @Min(1)
        private int topK = 3;
        private String retrievalUrl = "";
        private String llmUrl = "";
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public String getRetrievalUrl() {
            return retrievalUrl;
        }

        public void setRetrievalUrl(String retrievalUrl) {
            this.retrievalUrl = retrievalUrl;
        }

        public String getLlmUrl() {
            return llmUrl;
        }

        public void setLlmUrl(String llmUrl) {
            this.llmUrl = llmUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

}

