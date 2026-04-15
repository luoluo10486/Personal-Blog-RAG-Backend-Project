package com.personalblog.ragbackend.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 用户领域服务，封装系统用户的查询规则。
 */
@Service
public class MemberUserService {
    private static final String ACTIVE = "ACTIVE";

    private final MemberUserMapper memberUserMapper;

    public MemberUserService(MemberUserMapper memberUserMapper) {
        this.memberUserMapper = memberUserMapper;
    }

    public MemberUser findActiveByUsername(String username) {
        return memberUserMapper.selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getUsername, username)
                .eq(MemberUser::getStatus, ACTIVE)
                .last("limit 1"));
    }

    public MemberUser findActiveByPasswordAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }

        String normalizedAccount = account.trim();
        return memberUserMapper.selectOne(Wrappers.<MemberUser>lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(MemberUser::getUsername, normalizedAccount)
                        .or()
                        .eq(MemberUser::getPhone, normalizedAccount)
                        .or()
                        .eq(MemberUser::getEmail, normalizedAccount.toLowerCase(Locale.ROOT)))
                .eq(MemberUser::getStatus, ACTIVE)
                .last("limit 1"));
    }

    public MemberUser findActiveByPhone(String phone) {
        return memberUserMapper.selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getPhone, phone)
                .eq(MemberUser::getStatus, ACTIVE)
                .last("limit 1"));
    }

    public MemberUser findActiveByEmail(String email) {
        return memberUserMapper.selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email)
                .eq(MemberUser::getStatus, ACTIVE)
                .last("limit 1"));
    }

    public MemberUser findActiveById(Long userId) {
        return memberUserMapper.selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getUserId, userId)
                .eq(MemberUser::getStatus, ACTIVE)
                .last("limit 1"));
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return memberUserMapper.selectCount(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getUsername, username.trim())) > 0;
    }

    public boolean existsByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return memberUserMapper.selectCount(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getPhone, phone.trim())) > 0;
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return memberUserMapper.selectCount(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email.trim().toLowerCase(Locale.ROOT))) > 0;
    }

    public MemberUser create(MemberUser user) {
        memberUserMapper.insert(user);
        return user;
    }
}
