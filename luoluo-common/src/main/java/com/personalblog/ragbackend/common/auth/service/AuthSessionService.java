package com.personalblog.ragbackend.common.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.auth.domain.AuthSession;
import com.personalblog.ragbackend.common.auth.dto.AuthSessionCreateCommand;
import com.personalblog.ragbackend.common.auth.dto.AuthSessionResult;
import com.personalblog.ragbackend.common.auth.mapper.AuthSessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 通用认证会话服务，负责登录会话签发后的摘要落库、查询与撤销。
 */
@Service
public class AuthSessionService {
    private final AuthSessionMapper authSessionMapper;
    private final AuthDigestService authDigestService;

    public AuthSessionService(AuthSessionMapper authSessionMapper, AuthDigestService authDigestService) {
        this.authSessionMapper = authSessionMapper;
        this.authDigestService = authDigestService;
    }

    public AuthSessionResult createSession(AuthSessionCreateCommand command) {
        if (command.subjectId() == null) {
            throw new IllegalArgumentException("主体ID不能为空");
        }
        if (command.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("会话有效期必须大于 0");
        }
        long ttlSeconds = command.ttlSeconds();
        LocalDateTime now = LocalDateTime.now();
        String loginType = requireText(command.loginType(), "登录类型不能为空", true);
        String deviceType = command.deviceType() == null || command.deviceType().isBlank()
                ? loginType
                : requireText(command.deviceType(), "设备类型不能为空", true);

        StpUtil.login(command.subjectId(), new SaLoginParameter()
                .setDevice(deviceType)
                .setTimeout(ttlSeconds));
        String token = StpUtil.getTokenValue();

        AuthSession session = new AuthSession();
        session.setSubjectId(command.subjectId());
        session.setSubjectType(requireText(command.subjectType(), "主体类型不能为空", true));
        session.setLoginType(loginType);
        session.setTokenDigest(authDigestService.sha256Hex(token));
        session.setExpiresAt(now.plusSeconds(ttlSeconds));
        session.setRevoked(Boolean.FALSE);
        session.setDeleted(0);
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        session.setDeviceType(deviceType);
        session.setClientIp(normalizeNullable(command.clientIp(), false));
        authSessionMapper.insert(session);

        return new AuthSessionResult(session.getSessionId(), token, session.getExpiresAt());
    }

    public void logoutCurrentSession() {
        logoutByTokenValue(StpUtil.getTokenValue());
    }

    public void logoutByTokenValue(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String normalizedToken = token.trim();
        LocalDateTime now = LocalDateTime.now();
        authSessionMapper.update(null, Wrappers.<AuthSession>lambdaUpdate()
                .eq(AuthSession::getTokenDigest, authDigestService.sha256Hex(normalizedToken))
                .eq(AuthSession::getRevoked, Boolean.FALSE)
                .set(AuthSession::getRevoked, Boolean.TRUE)
                .set(AuthSession::getLastActiveAt, now));
        StpUtil.logoutByTokenValue(normalizedToken);
    }

    public Long getCurrentSubjectId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        return Long.parseLong(loginId.toString());
    }

    public AuthSession findActiveSessionByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return authSessionMapper.selectOne(Wrappers.<AuthSession>lambdaQuery()
                .eq(AuthSession::getTokenDigest, authDigestService.sha256Hex(token.trim()))
                .eq(AuthSession::getRevoked, Boolean.FALSE)
                .gt(AuthSession::getExpiresAt, LocalDateTime.now())
                .orderByDesc(AuthSession::getCreatedAt)
                .last("limit 1"));
    }

    private String normalizeText(String text, boolean upperCase) {
        String value = text == null ? "" : text.trim();
        return upperCase ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
    }

    private String requireText(String text, String message, boolean upperCase) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalizeText(text, upperCase);
    }

    private String normalizeNullable(String text, boolean upperCase) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return upperCase ? text.trim().toUpperCase(Locale.ROOT) : text.trim();
    }
}
