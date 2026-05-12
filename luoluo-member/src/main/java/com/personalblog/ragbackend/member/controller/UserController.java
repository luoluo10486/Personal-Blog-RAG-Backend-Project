package com.personalblog.ragbackend.member.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
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
    public R<CurrentUserVO> currentUser() {
        LoginUser user = UserContext.requireUser();
        return R.ok(new CurrentUserVO(
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                user.getAvatar()
        ));
    }

    @GetMapping("/users")
    public R<IPage<UserVO>> pageQuery(UserPageRequest request) {
        StpUtil.checkRole("admin");
        return R.ok(memberAdminUserService.pageQuery(request));
    }

    @PostMapping("/users")
    public R<String> create(@RequestBody UserCreateRequest request) {
        StpUtil.checkRole("admin");
        return R.ok(memberAdminUserService.create(request));
    }

    @PutMapping("/users/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        StpUtil.checkRole("admin");
        memberAdminUserService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/users/{id}")
    public R<Void> delete(@PathVariable String id) {
        StpUtil.checkRole("admin");
        memberAdminUserService.delete(id);
        return R.ok();
    }

    @PutMapping("/user/password")
    public R<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        memberAdminUserService.changePassword(request);
        return R.ok();
    }
}
