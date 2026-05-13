package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.member.application.MemberProfileApplicationService;
import com.personalblog.ragbackend.member.dto.profile.MemberProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资料控制器，对外提供当前登录用户信息查询接口。
 */
@RestController
@RequestMapping("/luoluo/member/profile")
public class MemberProfileController {
    private final MemberProfileApplicationService memberProfileApplicationService;

    public MemberProfileController(MemberProfileApplicationService memberProfileApplicationService) {
        this.memberProfileApplicationService = memberProfileApplicationService;
    }

    @GetMapping("/me")
    @MemberLoginRequired
    public Result<MemberProfileResponse> me() {
        return Results.success(memberProfileApplicationService.getCurrentProfile());
    }
}
