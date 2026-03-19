package com.personalblog.ragbackend.member.dto.auth;

public record MemberUserSummary(
        Long id,
        String username,
        String displayName,
        String phone,
        String email
) {
}
