package com.personalblog.ragbackend.member.application;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import com.personalblog.ragbackend.member.service.code.MemberSendCodeService;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务，编排会员登录与验证码发送用例。
 */
@Service
public class MemberAuthApplicationService {
    private final MemberAuthService memberAuthService;
    private final MemberSendCodeService memberSendCodeService;

    public MemberAuthApplicationService(
            MemberAuthService memberAuthService,
            MemberSendCodeService memberSendCodeService
    ) {
        this.memberAuthService = memberAuthService;
        this.memberSendCodeService = memberSendCodeService;
    }

    public MemberLoginResponse login(MemberLoginRequest request) {
        return memberAuthService.login(request);
    }

    public MemberSendVerifyCodeResponse sendCode(MemberSendVerifyCodeRequest request) {
        return memberSendCodeService.send(request);
    }
}
