package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * MemberAuthController 控制器，负责处理对外 HTTP 请求。
 */
@RestController
@RequestMapping("${app.api-prefix}/member/auth")
public class MemberAuthController {
    private final MemberAuthService memberAuthService;

    public MemberAuthController(MemberAuthService memberAuthService) {
        this.memberAuthService = memberAuthService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public MemberLoginResponse login(@Valid @RequestBody MemberLoginRequest request) {
        return memberAuthService.login(request);
    }
}

