package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * PasswordLoginStrategy 登录策略实现类，负责处理对应方式的认证逻辑。
 */
@Service
public class PasswordLoginStrategy implements MemberLoginStrategy {
    private final MemberUserMapper memberUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public PasswordLoginStrategy(
            MemberUserMapper memberUserMapper,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties
    ) {
        this.memberUserMapper = memberUserMapper;
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

        MemberUser user = memberUserMapper.selectActiveByUsername(username.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }

        boolean matched = matchesPassword(password, user.passwordHash());
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
            // 仅在本地或开发兼容模式下回退为明文比较。
        }
        return appProperties.getMember().getAuth().isAllowPlainPassword() && raw.equals(storedHash);
    }
}

