package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 密码登录策略，处理用户名加密码的认证流程。
 */
@Service
public class PasswordLoginStrategy implements MemberLoginStrategy {
    private final MemberUserService memberUserService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public PasswordLoginStrategy(
            MemberUserService memberUserService,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties
    ) {
        this.memberUserService = memberUserService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public String grantType() {
        return "password";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "username 和 password 不能为空");
        }

        MemberUser user = memberUserService.findActiveByUsername(username.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }

        boolean matched = matchesPassword(password, user.getPasswordHash());
        if (!matched) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }
        return user;
    }

    private boolean matchesPassword(String raw, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            if (passwordEncoder.matches(raw, storedHash)) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            // 兼容开发阶段的明文密码数据，生产环境应只保留加密密码。
        }
        return appProperties.getMember().getAuth().isAllowPlainPassword() && raw.equals(storedHash);
    }
}
