package com.personalblog.ragbackend.member.dto.auth;

/**
 * 用户摘要信息，用于登录成功后的轻量化展示。
 */
public record MemberUserSummary(
        Long id,
        String username,
        String displayName,
        String phone,
        String email,
        String userType
) {
}
