package com.personalblog.ragbackend.member.repository;

import com.personalblog.ragbackend.member.model.MemberSession;

import java.util.Optional;

public interface MemberSessionRepository {
    void save(MemberSession session);

    Optional<MemberSession> findValidByToken(String token);
}
