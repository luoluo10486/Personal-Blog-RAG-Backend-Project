package com.personalblog.ragbackend.member.config;

import cn.dev33.satoken.stp.StpInterface;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.service.MemberUserService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MemberSaTokenRoleConfig implements StpInterface {
    private final MemberUserService memberUserService;

    public MemberSaTokenRoleConfig(MemberUserService memberUserService) {
        this.memberUserService = memberUserService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        MemberUser user = memberUserService.findActiveById(userId);
        if (user == null || !StringUtils.hasText(user.getUserType())) {
            return List.of();
        }
        String role = user.getUserType().trim();
        List<String> roles = new ArrayList<>();
        roles.add(role);
        roles.add(role.toLowerCase(Locale.ROOT));
        return roles.stream().distinct().toList();
    }

    private Long parseUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
