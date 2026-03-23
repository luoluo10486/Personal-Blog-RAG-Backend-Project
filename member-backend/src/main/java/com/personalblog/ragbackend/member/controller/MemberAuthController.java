package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器，对外提供会员登录接口。
 */
@RestController
@RequestMapping("/luoluo/member/auth")
public class MemberAuthController {
    private final MemberAuthApplicationService memberAuthApplicationService;

    public MemberAuthController(MemberAuthApplicationService memberAuthApplicationService) {
        this.memberAuthApplicationService = memberAuthApplicationService;
    }

    @PostMapping("/login")
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return R.ok("登录成功", memberAuthApplicationService.login(request));
    }
}
