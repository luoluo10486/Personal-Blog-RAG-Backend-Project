package com.personalblog.ragbackend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级配置属性，集中管理会员认证相关参数。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Member member = new Member();

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
        @Min(60)
        private long verifyCodeTtlSeconds = 120;
        @Min(1)
        private long verifyCodeSendIntervalSeconds = 60;
        @Min(60)
        private long imageCaptchaTtlSeconds = 120;
        @Min(4)
        private int imageCaptchaLength = 4;
        private boolean imageCaptchaEnabled = false;
        private boolean allowMockVerifyCode = true;
        private String mockVerifyCode = "123456";
        private boolean allowPlainPassword = true;

        public long getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(long sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }

        public long getVerifyCodeTtlSeconds() {
            return verifyCodeTtlSeconds;
        }

        public void setVerifyCodeTtlSeconds(long verifyCodeTtlSeconds) {
            this.verifyCodeTtlSeconds = verifyCodeTtlSeconds;
        }

        public long getVerifyCodeSendIntervalSeconds() {
            return verifyCodeSendIntervalSeconds;
        }

        public void setVerifyCodeSendIntervalSeconds(long verifyCodeSendIntervalSeconds) {
            this.verifyCodeSendIntervalSeconds = verifyCodeSendIntervalSeconds;
        }

        public long getImageCaptchaTtlSeconds() {
            return imageCaptchaTtlSeconds;
        }

        public void setImageCaptchaTtlSeconds(long imageCaptchaTtlSeconds) {
            this.imageCaptchaTtlSeconds = imageCaptchaTtlSeconds;
        }

        public int getImageCaptchaLength() {
            return imageCaptchaLength;
        }

        public void setImageCaptchaLength(int imageCaptchaLength) {
            this.imageCaptchaLength = imageCaptchaLength;
        }

        public boolean isImageCaptchaEnabled() {
            return imageCaptchaEnabled;
        }

        public void setImageCaptchaEnabled(boolean imageCaptchaEnabled) {
            this.imageCaptchaEnabled = imageCaptchaEnabled;
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
