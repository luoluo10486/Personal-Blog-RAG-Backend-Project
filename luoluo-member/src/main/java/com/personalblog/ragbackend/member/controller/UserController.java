package com.personalblog.ragbackend.member.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.member.dto.user.ChangePasswordRequest;
import com.personalblog.ragbackend.member.dto.user.CurrentUserVO;
import com.personalblog.ragbackend.member.dto.user.UserCreateRequest;
import com.personalblog.ragbackend.member.dto.user.UserPageRequest;
import com.personalblog.ragbackend.member.dto.user.UserUpdateRequest;
import com.personalblog.ragbackend.member.dto.user.UserVO;
import com.personalblog.ragbackend.member.service.MemberAdminUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MemberLoginRequired
public class UserController {
    private final MemberAdminUserService memberAdminUserService;

    public UserController(MemberAdminUserService memberAdminUserService) {
        this.memberAdminUserService = memberAdminUserService;
    }

    @GetMapping("/user/me")
    public Result<CurrentUserVO> currentUser() {
        LoginUser user = UserContext.requireUser();
        return Results.success(new CurrentUserVO(
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                user.getAvatar()
        ));
    }

    @GetMapping("/users")
    public Result<IPage<UserVO>> pageQuery(UserPageRequest requestParam) {
        StpUtil.checkRole("admin");
        return Results.success(memberAdminUserService.pageQuery(requestParam));
    }

    @PostMapping("/users")
    public Result<String> create(@RequestBody UserCreateRequest requestParam) {
        StpUtil.checkRole("admin");
        return Results.success(memberAdminUserService.create(requestParam));
    }

    @PutMapping("/users/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody UserUpdateRequest requestParam) {
        StpUtil.checkRole("admin");
        memberAdminUserService.update(id, requestParam);
        return Results.success();
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable String id) {
        StpUtil.checkRole("admin");
        memberAdminUserService.delete(id);
        return Results.success();
    }

    @PutMapping("/user/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest requestParam) {
        memberAdminUserService.changePassword(requestParam);
        return Results.success();
    }
}
