package com.personalblog.ragbackend.member.model;

import java.time.LocalDateTime;

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
