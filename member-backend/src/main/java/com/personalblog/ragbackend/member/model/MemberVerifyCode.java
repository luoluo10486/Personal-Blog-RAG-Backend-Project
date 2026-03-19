package com.personalblog.ragbackend.member.model;

import java.time.LocalDateTime;

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
