package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员认证控制器。
 */
@RestController
@RequestMapping("/luoluo/member/auth")
public class MemberAuthController {
    private final MemberAuthApplicationService memberAuthApplicationService;

    public MemberAuthController(MemberAuthApplicationService memberAuthApplicationService) {
        this.memberAuthApplicationService = memberAuthApplicationService;
    }

    /**
     * 会员登录接口。
     */
    @PostMapping("/login")
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request, HttpServletRequest servletRequest) {
        return R.ok("登录成功", memberAuthApplicationService.login(request, resolveClientIp(servletRequest)));
    }

    /**
     * 会员退出登录接口。
     */
    @PostMapping("/logout")
    @MemberLoginRequired
    public R<Void> logout() {
        memberAuthApplicationService.logout();
        return R.ok("退出成功");
    }

    /**
     * 发送会员登录验证码。
     */
    @PostMapping("/send-code")
    public R<MemberSendVerifyCodeResponse> sendCode(@Valid @RequestBody MemberSendVerifyCodeRequest request) {
        return R.ok("验证码发送成功", memberAuthApplicationService.sendCode(request));
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
