package com.personalblog.ragbackend.member.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数，按不同登录方式承载对应的认证字段。
 */
public class MemberLoginRequest {
    @NotBlank
    private String grantType;
    private String username;
    private String password;
    private String phone;
    private String smsCode;
    private String email;
    private String emailCode;

    public String getGrantType() { return grantType; }
    public void setGrantType(String grantType) { this.grantType = grantType; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailCode() { return emailCode; }
    public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
}
