package com.personalblog.ragbackend.member.repository;

import com.personalblog.ragbackend.member.model.MemberVerifyCode;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberVerifyCodeRepository {
    Optional<MemberVerifyCode> findLatestAvailable(String targetType, String targetValue, LocalDateTime now);

    void markUsed(Long id);
}
