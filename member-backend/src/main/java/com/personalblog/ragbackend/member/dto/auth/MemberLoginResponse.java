package com.personalblog.ragbackend.member.dto.auth;

/**
 * MemberLoginResponse 数据传输对象，用于接口参数与返回值封装。
 */
public record MemberLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String grantType,
        MemberUserSummary user
) {
}

