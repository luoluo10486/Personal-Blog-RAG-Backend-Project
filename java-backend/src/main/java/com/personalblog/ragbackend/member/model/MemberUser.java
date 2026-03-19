package com.personalblog.ragbackend.member.model;

import java.time.LocalDateTime;

public record MemberUser(
        Long id,
        String username,
        String passwordHash,
        String phone,
        String email,
        String displayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
