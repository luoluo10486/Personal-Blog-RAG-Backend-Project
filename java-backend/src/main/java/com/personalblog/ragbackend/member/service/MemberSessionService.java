package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.model.MemberSession;
import com.personalblog.ragbackend.member.repository.MemberSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class MemberSessionService {
    private final MemberSessionRepository sessionRepository;
    private final AppProperties appProperties;

    public MemberSessionService(MemberSessionRepository sessionRepository, AppProperties appProperties) {
        this.sessionRepository = sessionRepository;
        this.appProperties = appProperties;
    }

    public MemberSession createSession(Long userId, String grantType) {
        long ttlSeconds = appProperties.getMember().getAuth().getSessionTtlSeconds();
        LocalDateTime now = LocalDateTime.now();
        MemberSession session = new MemberSession(
                null,
                userId,
                UUID.randomUUID().toString().replace("-", ""),
                grantType.toLowerCase(Locale.ROOT),
                now.plusSeconds(ttlSeconds),
                false,
                now
        );
        sessionRepository.save(session);
        return session;
    }

    public MemberSession requireValidSession(String bearerToken) {
        String token = extractToken(bearerToken);
        if (token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing access token");
        }
        return sessionRepository.findValidByToken(token)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid or expired access token"));
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
