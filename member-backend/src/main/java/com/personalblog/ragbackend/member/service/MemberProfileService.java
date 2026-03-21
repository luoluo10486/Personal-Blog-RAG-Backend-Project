package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.member.dto.profile.MemberProfileResponse;
import com.personalblog.ragbackend.member.domain.MemberSession;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * MemberProfileService 服务类，封装业务处理逻辑。
 */
@Service
public class MemberProfileService {
    private final MemberSessionService memberSessionService;
    private final MemberUserMapper memberUserMapper;

    public MemberProfileService(MemberSessionService memberSessionService, MemberUserMapper memberUserMapper) {
        this.memberSessionService = memberSessionService;
        this.memberUserMapper = memberUserMapper;
    }

    public MemberProfileResponse getCurrentProfile(String authorizationHeader) {
        MemberSession session = memberSessionService.requireValidSession(authorizationHeader);
        MemberUser user = memberUserMapper.selectById(session.userId());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户不存在");
        }
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

