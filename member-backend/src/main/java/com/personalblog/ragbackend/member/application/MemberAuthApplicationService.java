package com.personalblog.ragbackend.member.application;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import org.springframework.stereotype.Service;

/**
 * MemberAuthApplicationService 应用层服务，负责登录用例编排。
 */
@Service
public class MemberAuthApplicationService {
    private final MemberAuthService memberAuthService;

    public MemberAuthApplicationService(MemberAuthService memberAuthService) {
        this.memberAuthService = memberAuthService;
    }

    public MemberLoginResponse login(MemberLoginRequest request) {
        return memberAuthService.login(request);
    }
}

