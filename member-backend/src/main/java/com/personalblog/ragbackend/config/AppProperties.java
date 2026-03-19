package com.personalblog.ragbackend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String apiPrefix = "/api/v1";
    private final Member member = new Member();

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public Member getMember() {
        return member;
    }

    public static class Member {
        private final Auth auth = new Auth();

        public Auth getAuth() {
            return auth;
        }
    }

    public static class Auth {
        @Min(60)
        private long sessionTtlSeconds = 86400;
        private boolean allowMockVerifyCode = true;
        private String mockVerifyCode = "123456";
        private boolean allowPlainPassword = true;

        public long getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(long sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }

        public boolean isAllowMockVerifyCode() {
            return allowMockVerifyCode;
        }

        public void setAllowMockVerifyCode(boolean allowMockVerifyCode) {
            this.allowMockVerifyCode = allowMockVerifyCode;
        }

        public String getMockVerifyCode() {
            return mockVerifyCode;
        }

        public void setMockVerifyCode(String mockVerifyCode) {
            this.mockVerifyCode = mockVerifyCode;
        }

        public boolean isAllowPlainPassword() {
            return allowPlainPassword;
        }

        public void setAllowPlainPassword(boolean allowPlainPassword) {
            this.allowPlainPassword = allowPlainPassword;
        }
    }
}
