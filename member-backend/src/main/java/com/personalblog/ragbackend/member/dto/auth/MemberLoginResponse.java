package com.personalblog.ragbackend.member.dto.auth;

public record MemberLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String grantType,
        MemberUserSummary user
) {
}
