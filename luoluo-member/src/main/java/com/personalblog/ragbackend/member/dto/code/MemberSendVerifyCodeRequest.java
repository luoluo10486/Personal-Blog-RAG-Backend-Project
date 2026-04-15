package com.personalblog.ragbackend.member.dto.code;

import jakarta.validation.constraints.NotBlank;

/**
 * 验证码发送请求，统一承载短信和邮箱验证码发送参数。
 */
public class MemberSendVerifyCodeRequest {
    @NotBlank(message = "grantType 不能为空")
    private String grantType;

    private String bizType;
    private String captchaKey;
    private String captchaCode;
    private String phone;
    private String email;

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCaptchaKey() {
        return captchaKey;
    }

    public void setCaptchaKey(String captchaKey) {
        this.captchaKey = captchaKey;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
