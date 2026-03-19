package com.personalblog.ragbackend.member.controller;

import com.personalblog.ragbackend.member.dto.profile.MemberProfileResponse;
import com.personalblog.ragbackend.member.service.MemberProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api-prefix}/member/profile")
public class MemberProfileController {
    private final MemberProfileService memberProfileService;

    public MemberProfileController(MemberProfileService memberProfileService) {
        this.memberProfileService = memberProfileService;
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public MemberProfileResponse me(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return memberProfileService.getCurrentProfile(authorization);
    }
}
