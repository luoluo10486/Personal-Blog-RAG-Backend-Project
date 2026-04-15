package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberRegisterRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request, HttpServletRequest servletRequest) {
        return R.ok("login success", memberAuthApplicationService.login(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/register")
    public R<MemberLoginResponse> register(@Valid @RequestBody MemberRegisterRequest request, HttpServletRequest servletRequest) {
        return R.ok("register success", memberAuthApplicationService.register(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/logout")
    @MemberLoginRequired
    public R<Void> logout() {
        memberAuthApplicationService.logout();
        return R.ok("logout success");
    }

    @PostMapping("/send-code")
    public R<MemberSendVerifyCodeResponse> sendCode(@Valid @RequestBody MemberSendVerifyCodeRequest request) {
        return R.ok("send code success", memberAuthApplicationService.sendCode(request));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return null;
        }
        return remoteAddr.trim();
    }
}
