package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.AuthSessionCreateCommand;
import com.personalblog.ragbackend.common.auth.dto.AuthSessionResult;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * 会员登录会话服务，负责补齐会员侧会话创建参数并委托公共认证层处理。
 */
@Service
public class MemberSessionService {
    private static final String SUBJECT_TYPE = "SYS_USER";

    private final AuthSessionService authSessionService;
    private final AppProperties appProperties;

    public MemberSessionService(AuthSessionService authSessionService, AppProperties appProperties) {
        this.authSessionService = authSessionService;
        this.appProperties = appProperties;
    }

    public AuthSessionResult createSession(Long userId, String grantType) {
        return authSessionService.createSession(new AuthSessionCreateCommand(
                userId,
                SUBJECT_TYPE,
                grantType,
                appProperties.getMember().getAuth().getSessionTtlSeconds(),
                grantType,
                null
        ));
    }

    public Long getCurrentLoginUserId() {
        return authSessionService.getCurrentSubjectId();
    }
}
