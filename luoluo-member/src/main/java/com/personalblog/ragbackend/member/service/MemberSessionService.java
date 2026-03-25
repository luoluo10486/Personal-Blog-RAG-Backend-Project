package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.AuthSessionCreateCommand;
import com.personalblog.ragbackend.common.auth.dto.AuthSessionResult;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.springframework.stereotype.Service;

@Service
public class MemberSessionService {
    private static final String SUBJECT_TYPE = "SYS_USER";

    private final AuthSessionService authSessionService;
    private final MemberProperties memberProperties;

    public MemberSessionService(AuthSessionService authSessionService, MemberProperties memberProperties) {
        this.authSessionService = authSessionService;
        this.memberProperties = memberProperties;
    }

    public AuthSessionResult createSession(Long userId, String grantType) {
        return authSessionService.createSession(new AuthSessionCreateCommand(
                userId,
                SUBJECT_TYPE,
                grantType,
                memberProperties.getMember().getAuth().getSessionTtlSeconds(),
                grantType,
                null
        ));
    }

    public Long getCurrentLoginUserId() {
        return authSessionService.getCurrentSubjectId();
    }
}
