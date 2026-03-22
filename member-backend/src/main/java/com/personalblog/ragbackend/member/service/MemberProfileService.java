package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.profile.MemberProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 会员资料领域服务，负责当前登录用户资料读取。
 */
@Service
public class MemberProfileService {
    private final MemberSessionService memberSessionService;
    private final MemberUserService memberUserService;

    public MemberProfileService(MemberSessionService memberSessionService, MemberUserService memberUserService) {
        this.memberSessionService = memberSessionService;
        this.memberUserService = memberUserService;
    }

    public MemberProfileResponse getCurrentProfile() {
        Long userId = memberSessionService.getCurrentLoginUserId();
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户未登录");
        }

        MemberUser user = memberUserService.findActiveById(userId);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户不存在或状态不可用");
        }

        return new MemberProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhone(),
                user.getEmail(),
                user.getUserType(),
                user.getStatus()
        );
    }
}
