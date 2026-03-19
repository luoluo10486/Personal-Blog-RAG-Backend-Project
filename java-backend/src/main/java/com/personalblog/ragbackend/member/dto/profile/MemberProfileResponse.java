package com.personalblog.ragbackend.member.dto.profile;

public record MemberProfileResponse(
        Long id,
        String username,
        String displayName,
        String phone,
        String email,
        String status
) {
}
