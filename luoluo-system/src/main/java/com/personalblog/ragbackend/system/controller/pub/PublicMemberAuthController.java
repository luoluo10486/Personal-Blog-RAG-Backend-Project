package com.personalblog.ragbackend.system.controller.pub;

import cn.dev33.satoken.annotation.SaIgnore;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.system.application.PublicMemberAuthApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaIgnore
@RestController
@RequestMapping("/luoluo/system/public/member/auth")
public class PublicMemberAuthController {
    private final PublicMemberAuthApplicationService publicMemberAuthApplicationService;

    public PublicMemberAuthController(PublicMemberAuthApplicationService publicMemberAuthApplicationService) {
        this.publicMemberAuthApplicationService = publicMemberAuthApplicationService;
    }

    @PostMapping("/login")
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return R.ok("登录成功", publicMemberAuthApplicationService.login(request));
    }

    @PostMapping("/code/send")
    public R<MemberSendVerifyCodeResponse> sendCode(@Valid @RequestBody MemberSendVerifyCodeRequest request) {
        return R.ok("验证码发送成功", publicMemberAuthApplicationService.sendCode(request));
    }
}
