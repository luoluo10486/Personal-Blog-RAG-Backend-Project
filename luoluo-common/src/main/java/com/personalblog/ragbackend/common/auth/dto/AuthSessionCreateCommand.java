package com.personalblog.ragbackend.common.auth.dto;

/**
 * 会话创建命令，封装通用登录会话落库所需参数。
 */
public record AuthSessionCreateCommand(
        Long subjectId,
        String subjectType,
        String loginType,
        long ttlSeconds,
        String deviceType,
        String clientIp
) {
}
