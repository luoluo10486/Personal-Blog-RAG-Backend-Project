package com.personalblog.ragbackend.member.repository;

import com.personalblog.ragbackend.member.model.MemberUser;

import java.util.Optional;

/**
 * MemberUserRepository 定义仓储层数据访问能力。
 */
public interface MemberUserRepository {
    Optional<MemberUser> findActiveByUsername(String username);

    Optional<MemberUser> findActiveByPhone(String phone);

    Optional<MemberUser> findActiveByEmail(String email);

    Optional<MemberUser> findById(Long id);
}

