package com.personalblog.ragbackend.member.dto.auth;

/**
 * 登录响应数据，返回令牌信息和基础用户摘要。
 */
public record MemberLoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        String grantType,
        MemberUserSummary user
) {
}
