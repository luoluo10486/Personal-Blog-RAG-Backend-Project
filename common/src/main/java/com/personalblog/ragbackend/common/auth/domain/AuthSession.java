package com.personalblog.ragbackend.common.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 认证会话实体，用于保存平台级登录会话记录。
 */
@TableName("sys_auth_session")
public class AuthSession {
    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("subject_type")
    private String subjectType;

    @TableField("login_type")
    private String loginType;

    @TableField("token_digest")
    private String tokenDigest;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked")
    private Boolean revoked;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    @TableField("device_type")
    private String deviceType;

    @TableField("client_ip")
    private String clientIp;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(String tokenDigest) { this.tokenDigest = tokenDigest; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}
