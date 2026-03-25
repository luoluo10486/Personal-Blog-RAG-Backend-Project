package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/luoluo/member/auth")
public class MemberAuthController {
    private final MemberAuthApplicationService memberAuthApplicationService;

    public MemberAuthController(MemberAuthApplicationService memberAuthApplicationService) {
        this.memberAuthApplicationService = memberAuthApplicationService;
    }

    @PostMapping("/login")
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return R.ok("\u767b\u5f55\u6210\u529f", memberAuthApplicationService.login(request));
    }

    @PostMapping("/send-code")
    public R<MemberSendVerifyCodeResponse> sendCode(@Valid @RequestBody MemberSendVerifyCodeRequest request) {
        return R.ok("\u9a8c\u8bc1\u7801\u53d1\u9001\u6210\u529f", memberAuthApplicationService.sendCode(request));
    }
}
