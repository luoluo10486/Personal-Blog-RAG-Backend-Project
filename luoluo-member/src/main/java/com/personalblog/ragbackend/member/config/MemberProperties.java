package com.personalblog.ragbackend.member.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class MemberProperties {
    private final Member member = new Member();

    public Member getMember() {
        return member;
    }

    public static class Member {
        private final Auth auth = new Auth();
        private final Sms sms = new Sms();
        private final Email email = new Email();

        public Auth getAuth() {
            return auth;
        }

        public Sms getSms() {
            return sms;
        }

        public Email getEmail() {
            return email;
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
        private boolean allowMockVerifyCode = false;
        private String mockVerifyCode = "";
        private boolean allowPlainPassword = false;

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

    public static class Sms {
        private final Aliyun aliyun = new Aliyun();

        public Aliyun getAliyun() {
            return aliyun;
        }
    }

    public static class Email {
        private String loginSubject = "Login verification code";
        private String loginContentTemplate = "Your verification code is %s. It is valid for %d minutes.";

        public String getLoginSubject() {
            return loginSubject;
        }

        public void setLoginSubject(String loginSubject) {
            this.loginSubject = loginSubject;
        }

        public String getLoginContentTemplate() {
            return loginContentTemplate;
        }

        public void setLoginContentTemplate(String loginContentTemplate) {
            this.loginContentTemplate = loginContentTemplate;
        }
    }

    public static class Aliyun {
        private boolean enabled = false;
        private String accessKeyId;
        private String accessKeySecret;
        private String regionId = "cn-hangzhou";
        private String endpoint = "dysmsapi.aliyuncs.com";
        private String signName;
        private String loginTemplateCode;
        private String codeParamName = "code";
        private String ttlMinutesParamName = "ttl";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getLoginTemplateCode() {
            return loginTemplateCode;
        }

        public void setLoginTemplateCode(String loginTemplateCode) {
            this.loginTemplateCode = loginTemplateCode;
        }

        public String getCodeParamName() {
            return codeParamName;
        }

        public void setCodeParamName(String codeParamName) {
            this.codeParamName = codeParamName;
        }

        public String getTtlMinutesParamName() {
            return ttlMinutesParamName;
        }

        public void setTtlMinutesParamName(String ttlMinutesParamName) {
            this.ttlMinutesParamName = ttlMinutesParamName;
        }
    }
}
