package com.personalblog.ragbackend.member.repository;

import com.personalblog.ragbackend.member.model.MemberSession;

import java.util.Optional;

/**
 * MemberSessionRepository 定义仓储层数据访问能力。
 */
public interface MemberSessionRepository {
    void save(MemberSession session);

    Optional<MemberSession> findValidByToken(String token);
}

