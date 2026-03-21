package com.personalblog.ragbackend.member.repository;

import com.personalblog.ragbackend.member.model.MemberVerifyCode;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MemberVerifyCodeRepository 定义仓储层数据访问能力。
 */
public interface MemberVerifyCodeRepository {
    Optional<MemberVerifyCode> findLatestAvailable(String targetType, String targetValue, LocalDateTime now);

    void markUsed(Long id);
}

