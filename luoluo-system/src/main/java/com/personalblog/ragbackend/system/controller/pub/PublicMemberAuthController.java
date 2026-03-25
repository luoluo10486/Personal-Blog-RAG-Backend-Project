package com.personalblog.ragbackend.system.controller.pub;

import cn.dev33.satoken.annotation.SaIgnore;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.system.application.PublicMemberAuthApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员公共认证控制器，对外提供无需登录即可访问的会员认证公共接口。
 */
@SaIgnore
@RestController
@RequestMapping("/luoluo/system/public/member/auth")
public class PublicMemberAuthController {
    private final PublicMemberAuthApplicationService publicMemberAuthApplicationService;

    public PublicMemberAuthController(PublicMemberAuthApplicationService publicMemberAuthApplicationService) {
        this.publicMemberAuthApplicationService = publicMemberAuthApplicationService;
    }

    @PostMapping("/code/send")
    public R<MemberSendVerifyCodeResponse> sendCode(@Valid @RequestBody MemberSendVerifyCodeRequest request) {
        return R.ok("验证码发送成功", publicMemberAuthApplicationService.sendCode(request));
    }
}
