package com.personalblog.ragbackend.member.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.user.ChangePasswordRequest;
import com.personalblog.ragbackend.member.dto.user.UserCreateRequest;
import com.personalblog.ragbackend.member.dto.user.UserPageRequest;
import com.personalblog.ragbackend.member.dto.user.UserUpdateRequest;
import com.personalblog.ragbackend.member.dto.user.UserVO;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberAdminUserService {
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemberUserMapper memberUserMapper;
    private final PasswordEncoder passwordEncoder;

    public MemberAdminUserService(MemberUserMapper memberUserMapper,
                                  PasswordEncoder passwordEncoder) {
        this.memberUserMapper = memberUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public IPage<UserVO> pageQuery(UserPageRequest request) {
        String keyword = trimToNull(request == null ? null : request.getKeyword());
        long current = request == null ? 1 : Math.max(request.getCurrent(), 1);
        long size = request == null ? 10 : Math.max(request.getSize(), 1);
        Page<MemberUser> page = new Page<>(current, size);
        IPage<MemberUser> result = memberUserMapper.selectPage(
                page,
                Wrappers.<MemberUser>lambdaQuery()
                        .eq(MemberUser::getDeleted, 0)
                        .and(keyword != null, wrapper -> wrapper
                                .like(MemberUser::getUsername, keyword)
                                .or()
                                .like(MemberUser::getUserType, keyword))
                        .orderByDesc(MemberUser::getUpdatedAt)
        );
        return result.convert(this::toVO);
    }

    public String create(UserCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String username = trimToNull(request.getUsername());
        String password = trimToNull(request.getPassword());
        if (username == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("默认管理员用户名不可用");
        }
        ensureUsernameAvailable(username, null);

        MemberUser entity = new MemberUser();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setUserType(normalizeRole(request.getRole()));
        entity.setStatus(STATUS_ACTIVE);
        entity.setDisplayName(username);
        memberUserMapper.insert(entity);
        return String.valueOf(entity.getUserId());
    }

    public void update(String id, UserUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        MemberUser entity = loadById(id);
        ensureNotDefaultAdmin(entity);

        if (request.getUsername() != null) {
            String username = trimToNull(request.getUsername());
            if (username == null) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            if (!username.equals(entity.getUsername())) {
                if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                    throw new IllegalArgumentException("默认管理员用户名不可用");
                }
                ensureUsernameAvailable(username, entity.getUserId());
            }
            entity.setUsername(username);
            if (trimToNull(entity.getDisplayName()) == null) {
                entity.setDisplayName(username);
            }
        }
        if (request.getRole() != null) {
            entity.setUserType(normalizeRole(request.getRole()));
        }
        if (request.getPassword() != null) {
            String password = trimToNull(request.getPassword());
            if (password == null) {
                throw new IllegalArgumentException("新密码不能为空");
            }
            entity.setPasswordHash(passwordEncoder.encode(password));
        }
        memberUserMapper.updateById(entity);
    }

    public void delete(String id) {
        MemberUser entity = loadById(id);
        ensureNotDefaultAdmin(entity);
        memberUserMapper.deleteById(entity.getUserId());
    }

    public void changePassword(ChangePasswordRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String current = trimToNull(request.getCurrentPassword());
        String next = trimToNull(request.getNewPassword());
        if (current == null) {
            throw new IllegalArgumentException("当前密码不能为空");
        }
        if (next == null) {
            throw new IllegalArgumentException("新密码不能为空");
        }

        LoginUser loginUser = UserContext.requireUser();
        MemberUser entity = memberUserMapper.selectOne(
                Wrappers.<MemberUser>lambdaQuery()
                        .eq(MemberUser::getUserId, Long.valueOf(loginUser.getUserId()))
                        .eq(MemberUser::getDeleted, 0)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!passwordEncoder.matches(current, entity.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        entity.setPasswordHash(passwordEncoder.encode(next));
        memberUserMapper.updateById(entity);
    }

    private MemberUser loadById(String id) {
        Long userId;
        try {
            userId = Long.valueOf(id);
        } catch (Exception exception) {
            throw new IllegalArgumentException("用户不存在");
        }
        MemberUser entity = memberUserMapper.selectOne(
                Wrappers.<MemberUser>lambdaQuery()
                        .eq(MemberUser::getUserId, userId)
                        .eq(MemberUser::getDeleted, 0)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return entity;
    }

    private void ensureNotDefaultAdmin(MemberUser entity) {
        if (entity != null && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(entity.getUsername())) {
            throw new IllegalArgumentException("默认管理员不允许修改或删除");
        }
    }

    private void ensureUsernameAvailable(String username, Long excludeUserId) {
        MemberUser existing = memberUserMapper.selectOne(
                Wrappers.<MemberUser>lambdaQuery()
                        .eq(MemberUser::getUsername, username)
                        .eq(MemberUser::getDeleted, 0)
                        .ne(excludeUserId != null, MemberUser::getUserId, excludeUserId)
                        .last("limit 1")
        );
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private String normalizeRole(String role) {
        String normalized = trimToNull(role);
        if (normalized == null) {
            return ROLE_USER;
        }
        if (ROLE_ADMIN.equalsIgnoreCase(normalized)) {
            return ROLE_ADMIN;
        }
        if (ROLE_USER.equalsIgnoreCase(normalized)) {
            return ROLE_USER;
        }
        throw new IllegalArgumentException("角色类型不合法");
    }

    private UserVO toVO(MemberUser entity) {
        UserVO vo = new UserVO();
        vo.setId(String.valueOf(entity.getUserId()));
        vo.setUsername(entity.getUsername());
        vo.setRole(entity.getUserType());
        vo.setAvatar(null);
        vo.setCreateTime(entity.getCreatedAt());
        vo.setUpdateTime(entity.getUpdatedAt());
        return vo;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
