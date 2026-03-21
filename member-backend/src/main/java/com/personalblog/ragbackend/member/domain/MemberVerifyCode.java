package com.personalblog.ragbackend.member.domain;

import java.time.LocalDateTime;

/**
 * MemberVerifyCode 领域模型，描述业务数据结构。
 */
public record MemberVerifyCode(
        Long id,
        String targetType,
        String targetValue,
        String verifyCode,
        LocalDateTime expiresAt,
        boolean used,
        LocalDateTime createdAt
) {
}

