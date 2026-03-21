package com.personalblog.ragbackend.member.domain;

import java.time.LocalDateTime;

/**
 * MemberUser 领域模型，描述业务数据结构。
 */
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

