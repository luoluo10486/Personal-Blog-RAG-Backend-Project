package com.personalblog.ragbackend.member.dto.auth;

/**
 * MemberUserSummary 数据传输对象，用于接口参数与返回值封装。
 */
public record MemberUserSummary(
        Long id,
        String username,
        String displayName,
        String phone,
        String email
) {
}

