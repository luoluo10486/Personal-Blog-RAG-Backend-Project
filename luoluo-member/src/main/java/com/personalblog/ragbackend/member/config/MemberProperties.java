package com.personalblog.ragbackend.member.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会员模块配置项。
 * 对应 application.yml 中的 app.* 配置。
 */
@ConfigurationProperties(prefix = "app")
public class MemberProperties {
    /**
     * 会员相关配置根节点。
     */
    private final Member member = new Member();

    public Member getMember() {
        return member;
    }

    /**
     * 会员模块聚合配置。
     */
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

    /**
     * 会员认证配置。
     */
    public static class Auth {
        /**
         * 登录会话有效期，单位秒。
         */
        @Min(60)
        private long sessionTtlSeconds = 86400;
        /**
         * 验证码有效期，单位秒。
         */
        @Min(60)
        private long verifyCodeTtlSeconds = 120;
        /**
         * 同一目标的发送间隔，单位秒。
         */
        @Min(1)
        private long verifyCodeSendIntervalSeconds = 60;
        /**
         * 图形验证码有效期，单位秒。
         */
        @Min(60)
        private long imageCaptchaTtlSeconds = 120;
        /**
         * 图形验证码长度。
         */
        @Min(4)
        private int imageCaptchaLength = 4;
        /**
         * 是否启用图形验证码校验。
         */
        private boolean imageCaptchaEnabled = false;
        /**
         * 是否允许在日志中输出明文验证码。
         */
        private boolean plainVerifyCodeLogEnabled = false;
        /**
         * 是否允许返回 mock 验证码，便于本地调试。
         */
        private boolean allowMockVerifyCode = false;
        /**
         * mock 模式下允许通过的固定验证码。
         */
        private String mockVerifyCode = "";
        /**
         * 是否允许明文密码直传登录。
         */
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

        public boolean isPlainVerifyCodeLogEnabled() {
            return plainVerifyCodeLogEnabled;
        }

        public void setPlainVerifyCodeLogEnabled(boolean plainVerifyCodeLogEnabled) {
            this.plainVerifyCodeLogEnabled = plainVerifyCodeLogEnabled;
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

    /**
     * 短信发送配置。
     */
    public static class Sms {
        private final Aliyun aliyun = new Aliyun();

        public Aliyun getAliyun() {
            return aliyun;
        }
    }

    /**
     * 邮件发送配置。
     */
    public static class Email {
        /**
         * 登录验证码邮件主题。
         */
        private String loginSubject = "Login verification code";
        /**
         * 登录验证码邮件模板，依次注入验证码和有效分钟数。
         */
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

    /**
     * 阿里云短信配置。
     */
    public static class Aliyun {
        /**
         * 是否启用阿里云短信通道。
         */
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
