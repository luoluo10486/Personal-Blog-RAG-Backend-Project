package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.member.dto.profile.MemberProfileResponse;
import com.personalblog.ragbackend.member.model.MemberSession;
import com.personalblog.ragbackend.member.model.MemberUser;
import com.personalblog.ragbackend.member.repository.MemberUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class MemberProfileService {
    private final MemberSessionService memberSessionService;
    private final MemberUserRepository memberUserRepository;

    public MemberProfileService(MemberSessionService memberSessionService, MemberUserRepository memberUserRepository) {
        this.memberSessionService = memberSessionService;
        this.memberUserRepository = memberUserRepository;
    }

    public MemberProfileResponse getCurrentProfile(String authorizationHeader) {
        MemberSession session = memberSessionService.requireValidSession(authorizationHeader);
        MemberUser user = memberUserRepository.findById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User does not exist"));
        return new MemberProfileResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.phone(),
                user.email(),
                user.status()
        );
    }
}
