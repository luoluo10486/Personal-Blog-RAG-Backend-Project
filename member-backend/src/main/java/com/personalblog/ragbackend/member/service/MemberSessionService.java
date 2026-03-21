package com.personalblog.ragbackend.member.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.domain.MemberSession;
import com.personalblog.ragbackend.member.mapper.MemberSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * MemberSessionService 服务类，封装业务处理逻辑。
 */
@Service
public class MemberSessionService {
    private final MemberSessionMapper memberSessionMapper;
    private final AppProperties appProperties;

    public MemberSessionService(MemberSessionMapper memberSessionMapper, AppProperties appProperties) {
        this.memberSessionMapper = memberSessionMapper;
        this.appProperties = appProperties;
    }

    public MemberSession createSession(Long userId, String grantType) {
        long ttlSeconds = appProperties.getMember().getAuth().getSessionTtlSeconds();
        LocalDateTime now = LocalDateTime.now();
        StpUtil.login(userId, new SaLoginParameter()
                .setDevice(grantType.toLowerCase(Locale.ROOT))
                .setTimeout(ttlSeconds));
        String token = StpUtil.getTokenValue();
        MemberSession session = new MemberSession(
                null,
                userId,
                token,
                grantType.toLowerCase(Locale.ROOT),
                now.plusSeconds(ttlSeconds),
                false,
                now
        );
        memberSessionMapper.insertSession(
                session.userId(),
                session.token(),
                session.grantType(),
                session.expiresAt(),
                session.revoked(),
                session.createdAt()
        );
        return session;
    }

    public MemberSession requireValidSession(String bearerToken) {
        String token = extractToken(bearerToken);
        if (token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "缺少访问令牌");
        }
        MemberSession session = memberSessionMapper.selectValidByToken(token);
        if (session == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "访问令牌无效或已过期");
        }
        return session;
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return "";
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}

