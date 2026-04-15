package com.personalblog.ragbackend.member.application;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberRegisterRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import com.personalblog.ragbackend.member.service.MemberRegisterService;
import com.personalblog.ragbackend.member.service.code.MemberSendCodeService;
import org.springframework.stereotype.Service;

@Service
public class MemberAuthApplicationService {
    private final MemberAuthService memberAuthService;
    private final MemberRegisterService memberRegisterService;
    private final MemberSendCodeService memberSendCodeService;

    public MemberAuthApplicationService(
            MemberAuthService memberAuthService,
            MemberRegisterService memberRegisterService,
            MemberSendCodeService memberSendCodeService
    ) {
        this.memberAuthService = memberAuthService;
        this.memberRegisterService = memberRegisterService;
        this.memberSendCodeService = memberSendCodeService;
    }

    public MemberLoginResponse login(MemberLoginRequest request, String clientIp) {
        return memberAuthService.login(request, clientIp);
    }

    public void logout() {
        memberAuthService.logoutCurrentSession();
    }

    public MemberLoginResponse register(MemberRegisterRequest request, String clientIp) {
        return memberRegisterService.register(request, clientIp);
    }

    public MemberSendVerifyCodeResponse sendCode(MemberSendVerifyCodeRequest request) {
        return memberSendCodeService.send(request);
    }
}
