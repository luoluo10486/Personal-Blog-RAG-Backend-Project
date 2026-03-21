package com.personalblog.ragbackend.member.domain;

import java.time.LocalDateTime;

/**
 * MemberSession 领域模型，描述业务数据结构。
 */
public record MemberSession(
        Long id,
        Long userId,
        String token,
        String grantType,
        LocalDateTime expiresAt,
        boolean revoked,
        LocalDateTime createdAt
) {
}

