package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.AuthSessionResult;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberUserSummary;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 会员认证领域服务，负责根据登录方式完成认证并签发会话。
 */
@Service
public class MemberAuthService {
    private final Map<String, MemberLoginStrategy> strategyMap;
    private final MemberSessionService memberSessionService;

    public MemberAuthService(List<MemberLoginStrategy> strategies, MemberSessionService memberSessionService) {
        this.strategyMap = strategies.stream().collect(Collectors.toMap(
                strategy -> strategy.grantType().toLowerCase(Locale.ROOT),
                Function.identity()
        ));
        this.memberSessionService = memberSessionService;
    }

    public MemberLoginResponse login(MemberLoginRequest request, String clientIp) {
        String grantType = normalizeGrantType(request.getGrantType());
        MemberLoginStrategy strategy = strategyMap.get(grantType);
        if (strategy == null) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的 grantType：" + grantType);
        }

        MemberUser user = strategy.authenticate(request);
        AuthSessionResult session = memberSessionService.createSession(
                user.getUserId(),
                grantType,
                normalizeNullable(request.getDeviceType()),
                normalizeNullable(clientIp)
        );
        long expiresIn = Duration.between(LocalDateTime.now(), session.expiresAt()).toSeconds();

        return new MemberLoginResponse(
                session.token(),
                "Bearer",
                Math.max(expiresIn, 0),
                grantType,
                new MemberUserSummary(
                        user.getUserId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getPhone(),
                        user.getEmail(),
                        user.getUserType()
                )
        );
    }

    public void logoutCurrentSession() {
        memberSessionService.logoutCurrentSession();
    }

    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "grantType 不能为空");
        }
        return grantType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
